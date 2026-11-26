package pl.szkola.radiowezel.remote

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pl.szkola.radiowezel.BuildConfig
import pl.szkola.radiowezel.RadiowezelApp
import pl.szkola.radiowezel.alarm.AlarmType
import pl.szkola.radiowezel.player.AlarmPlayerService
import pl.szkola.radiowezel.scheduler.BellScheduler

/**
 * Listens for remote alarm commands on Firebase Realtime Database:
 * devices/{deviceId}/command → start/stop; writes ack + presence.
 */
class CommandListener(private val app: RadiowezelApp) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var commandListener: ValueEventListener? = null
    private var heartbeatJob: Job? = null
    private var lastCommandId: String? = null
    private var started = false

    fun start() {
        if (started) return
        scope.launch {
            try {
                val cfg = FirebaseConfigLoader.load(app)
                if (!cfg.isConfigured) {
                    Log.w(TAG, "Firebase config missing/placeholder — remote disabled")
                    return@launch
                }
                ensureFirebaseApp(cfg)
                val auth = FirebaseAuth.getInstance()
                if (auth.currentUser == null) {
                    auth.signInWithEmailAndPassword(cfg.deviceEmail, cfg.devicePassword).await()
                }
                val deviceId = app.settingsRepository.ensureDeviceId()
                val db = FirebaseDatabase.getInstance(cfg.databaseURL)
                val deviceRef = db.getReference("devices").child(deviceId)
                deviceRef.child("presence").updateChildren(
                    mapOf(
                        "online" to true,
                        "lastSeen" to ServerValue.TIMESTAMP,
                        "appVersion" to BuildConfig.VERSION_NAME,
                        "nextBell" to (BellScheduler(app).findNextBellLabel() ?: "")
                    )
                )
                deviceRef.child("presence").onDisconnect().updateChildren(
                    mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)
                )

                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        val id = snapshot.child("id").getValue(String::class.java) ?: return
                        if (id == lastCommandId) return
                        val type = snapshot.child("type").getValue(String::class.java) ?: return
                        val alarmTypeId = snapshot.child("alarmType").getValue(String::class.java)
                        val drill = snapshot.child("drill").getValue(Boolean::class.java) ?: true
                        lastCommandId = id
                        scope.launch {
                            handleCommand(deviceId, id, type, alarmTypeId, drill, cfg.databaseURL)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "command listen cancelled: ${error.message}")
                    }
                }
                deviceRef.child("command").addValueEventListener(listener)
                commandListener = listener
                started = true
                heartbeatJob = scope.launch {
                    while (isActive) {
                        delay(45_000)
                        try {
                            val next = BellScheduler(app).findNextBellLabel()
                            db.getReference("devices").child(deviceId).child("presence").updateChildren(
                                mapOf(
                                    "online" to true,
                                    "lastSeen" to ServerValue.TIMESTAMP,
                                    "appVersion" to BuildConfig.VERSION_NAME,
                                    "nextBell" to (next ?: "")
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "heartbeat failed", e)
                        }
                    }
                }
                Log.i(TAG, "Remote command listener started for $deviceId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CommandListener", e)
                started = false
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        try {
            val cfg = FirebaseConfigLoader.load(app)
            if (cfg.isConfigured && commandListener != null) {
                scope.launch {
                    val deviceId = app.settingsRepository.settings.first().deviceId
                    if (deviceId.isNotBlank()) {
                        FirebaseDatabase.getInstance(cfg.databaseURL)
                            .getReference("devices").child(deviceId).child("command")
                            .removeEventListener(commandListener!!)
                    }
                }
            }
        } catch (_: Exception) {
        }
        commandListener = null
        started = false
    }

    private suspend fun handleCommand(
        deviceId: String,
        commandId: String,
        type: String,
        alarmTypeId: String?,
        drill: Boolean,
        databaseURL: String
    ) {
        val db = FirebaseDatabase.getInstance(databaseURL)
        val ackRef = db.getReference("devices").child(deviceId).child("ack")
        try {
            when (type.lowercase()) {
                "stop" -> {
                    AlarmPlayerService.stop(app)
                    ackRef.setValue(
                        mapOf(
                            "commandId" to commandId,
                            "status" to "stopped",
                            "at" to ServerValue.TIMESTAMP
                        )
                    ).await()
                }
                "start", "alarm" -> {
                    val alarm = AlarmType.fromId(alarmTypeId.orEmpty())
                    if (alarm == null) {
                        ackRef.setValue(
                            mapOf(
                                "commandId" to commandId,
                                "status" to "ignored_unknown_type",
                                "at" to ServerValue.TIMESTAMP
                            )
                        ).await()
                        return
                    }
                    AlarmPlayerService.start(app, alarm, drill)
                    ackRef.setValue(
                        mapOf(
                            "commandId" to commandId,
                            "status" to "started",
                            "alarmType" to alarm.id,
                            "at" to ServerValue.TIMESTAMP
                        )
                    ).await()
                    db.getReference("audit").push().setValue(
                        mapOf(
                            "deviceId" to deviceId,
                            "commandId" to commandId,
                            "alarmType" to alarm.id,
                            "drill" to drill,
                            "at" to ServerValue.TIMESTAMP,
                            "source" to "remote"
                        )
                    )
                }
                else -> {
                    ackRef.setValue(
                        mapOf(
                            "commandId" to commandId,
                            "status" to "ignored_unknown_command",
                            "at" to ServerValue.TIMESTAMP
                        )
                    ).await()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleCommand failed", e)
            try {
                ackRef.setValue(
                    mapOf(
                        "commandId" to commandId,
                        "status" to "error",
                        "message" to (e.message ?: "error"),
                        "at" to ServerValue.TIMESTAMP
                    )
                ).await()
            } catch (_: Exception) {
            }
        }
    }

    private fun ensureFirebaseApp(cfg: FirebaseConfig) {
        if (FirebaseApp.getApps(app).isNotEmpty()) return
        val options = FirebaseOptions.Builder()
            .setApiKey(cfg.apiKey)
            .setApplicationId(cfg.appId)
            .setProjectId(cfg.projectId)
            .setDatabaseUrl(cfg.databaseURL)
            .setGcmSenderId(cfg.messagingSenderId)
            .setStorageBucket(cfg.storageBucket)
            .build()
        FirebaseApp.initializeApp(app, options)
    }

    companion object {
        private const val TAG = "CommandListener"
    }
}
