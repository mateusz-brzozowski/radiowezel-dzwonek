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
import pl.szkola.radiowezel.data.SchoolSchedule
import pl.szkola.radiowezel.data.db.BellEventEntity
import pl.szkola.radiowezel.data.db.BellType
import pl.szkola.radiowezel.data.db.HolidayEntity
import pl.szkola.radiowezel.data.db.TrackEntity
import pl.szkola.radiowezel.player.AlarmPlayerService
import pl.szkola.radiowezel.player.BellPlayerService
import pl.szkola.radiowezel.scheduler.BellScheduler
import pl.szkola.radiowezel.util.TrackStorage
import java.io.File

/**
 * Remote control bus:
 * devices/{deviceId}/command → alarm + schedule/tracks/holidays CRUD
 * devices/{deviceId}/state → mirror of Room data for the web panel
 */
class CommandListener(private val app: RadiowezelApp) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var commandListener: ValueEventListener? = null
    private var heartbeatJob: Job? = null
    private var lastCommandId: String? = null
    private var started = false
    private var databaseURL: String = ""

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
                databaseURL = cfg.databaseURL
                val auth = FirebaseAuth.getInstance()
                if (auth.currentUser == null) {
                    auth.signInWithEmailAndPassword(cfg.deviceEmail, cfg.devicePassword).await()
                }
                val deviceId = app.settingsRepository.ensureDeviceId()
                val db = FirebaseDatabase.getInstance(cfg.databaseURL)
                val deviceRef = db.getReference("devices").child(deviceId)
                publishPresence(deviceId)
                publishState(deviceId)
                deviceRef.child("presence").onDisconnect().updateChildren(
                    mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)
                )

                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        val id = snapshot.child("id").getValue(String::class.java) ?: return
                        if (id == lastCommandId) return
                        lastCommandId = id
                        scope.launch { handleCommand(deviceId, snapshot) }
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
                            publishPresence(deviceId)
                            publishState(deviceId)
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
            if (databaseURL.isNotBlank() && commandListener != null) {
                scope.launch {
                    val deviceId = app.settingsRepository.settings.first().deviceId
                    if (deviceId.isNotBlank()) {
                        FirebaseDatabase.getInstance(databaseURL)
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

    private suspend fun publishPresence(deviceId: String) {
        val next = BellScheduler(app).findNextBellLabel()
        FirebaseDatabase.getInstance(databaseURL)
            .getReference("devices").child(deviceId).child("presence")
            .updateChildren(
                mapOf(
                    "online" to true,
                    "lastSeen" to ServerValue.TIMESTAMP,
                    "appVersion" to BuildConfig.VERSION_NAME,
                    "nextBell" to (next ?: "")
                )
            ).await()
    }

    private suspend fun publishState(deviceId: String) {
        val events = app.database.bellEventDao().getAll()
        val tracks = app.database.trackDao().getAll()
        val holidays = app.database.holidayDao().getAll()
        val payload = mapOf(
            "updatedAt" to ServerValue.TIMESTAMP,
            "events" to events.map { e ->
                mapOf(
                    "id" to e.id,
                    "timeMinutes" to e.timeMinutes,
                    "type" to e.type.name,
                    "trackId" to e.trackId,
                    "durationSeconds" to e.durationSeconds,
                    "enabled" to e.enabled
                )
            },
            "tracks" to tracks.map { t ->
                mapOf(
                    "id" to t.id,
                    "name" to t.name,
                    "createdAt" to t.createdAt
                )
            },
            "holidays" to holidays.map { h ->
                mapOf(
                    "id" to h.id,
                    "date" to h.date,
                    "name" to h.name,
                    "builtIn" to h.builtIn
                )
            }
        )
        FirebaseDatabase.getInstance(databaseURL)
            .getReference("devices").child(deviceId).child("state")
            .setValue(payload)
            .await()
    }

    private suspend fun handleCommand(deviceId: String, snapshot: DataSnapshot) {
        val commandId = snapshot.child("id").getValue(String::class.java) ?: return
        val type = snapshot.child("type").getValue(String::class.java) ?: return
        val ackRef = FirebaseDatabase.getInstance(databaseURL)
            .getReference("devices").child(deviceId).child("ack")
        try {
            when (type.lowercase()) {
                "stop" -> {
                    AlarmPlayerService.stop(app)
                    ackOk(ackRef, commandId, "stopped")
                }
                "start", "alarm" -> {
                    val alarmTypeId = snapshot.child("alarmType").getValue(String::class.java)
                    val drill = snapshot.child("drill").getValue(Boolean::class.java) ?: true
                    val alarm = AlarmType.fromId(alarmTypeId.orEmpty())
                        ?: run {
                            ackOk(ackRef, commandId, "ignored_unknown_type")
                            return
                        }
                    AlarmPlayerService.start(app, alarm, drill)
                    ackOk(ackRef, commandId, "started", mapOf("alarmType" to alarm.id))
                    FirebaseDatabase.getInstance(databaseURL).getReference("audit").push().setValue(
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
                "ring_now" -> {
                    val trackId = snapshot.longVal("trackId")
                    val duration = snapshot.intVal("durationSeconds") ?: 15
                    val track = trackId?.let { app.database.trackDao().getById(it) }
                        ?: app.database.trackDao().getAll().firstOrNull()
                    BellPlayerService.play(
                        context = app,
                        trackPath = track?.filePath,
                        durationSec = duration,
                        title = "Zdalny test"
                    )
                    ackOk(ackRef, commandId, "ringing")
                }
                "upsert_event" -> {
                    val id = snapshot.longVal("eventId") ?: 0L
                    val timeMinutes = snapshot.intVal("timeMinutes")
                        ?: error("timeMinutes required")
                    val typeName = snapshot.child("bellType").getValue(String::class.java)
                        ?: BellType.BREAK_START.name
                    val bellType = runCatching { BellType.valueOf(typeName) }.getOrDefault(BellType.BREAK_START)
                    val trackId = snapshot.longVal("trackId")
                    val duration = snapshot.intVal("durationSeconds") ?: 60
                    val enabled = snapshot.child("enabled").getValue(Boolean::class.java) ?: true
                    app.database.bellEventDao().upsert(
                        BellEventEntity(
                            id = id,
                            timeMinutes = timeMinutes,
                            type = bellType,
                            trackId = trackId,
                            durationSeconds = duration,
                            enabled = enabled
                        )
                    )
                    BellScheduler(app).rescheduleAll()
                    publishState(deviceId)
                    publishPresence(deviceId)
                    ackOk(ackRef, commandId, "event_saved")
                }
                "delete_event" -> {
                    val id = snapshot.longVal("eventId")
                        ?: error("eventId required")
                    val event = app.database.bellEventDao().getById(id)
                    if (event != null) app.database.bellEventDao().delete(event)
                    BellScheduler(app).rescheduleAll()
                    publishState(deviceId)
                    publishPresence(deviceId)
                    ackOk(ackRef, commandId, "event_deleted")
                }
                "apply_school_schedule" -> {
                    val tracks = app.database.trackDao().getAll()
                    app.database.bellEventDao().deleteAll()
                    SchoolSchedule.buildEvents(tracks).forEach { app.database.bellEventDao().upsert(it) }
                    app.settingsRepository.setScheduleTemplateVersion(SchoolSchedule.TEMPLATE_VERSION)
                    BellScheduler(app).rescheduleAll()
                    publishState(deviceId)
                    publishPresence(deviceId)
                    ackOk(ackRef, commandId, "schedule_applied")
                }
                "upsert_holiday" -> {
                    val id = snapshot.longVal("holidayId") ?: 0L
                    val date = snapshot.child("date").getValue(String::class.java)
                        ?: error("date required")
                    val name = snapshot.child("name").getValue(String::class.java) ?: "Dzień wolny"
                    app.database.holidayDao().insert(
                        HolidayEntity(id = id, date = date, name = name, builtIn = false)
                    )
                    BellScheduler(app).rescheduleAll()
                    publishState(deviceId)
                    publishPresence(deviceId)
                    ackOk(ackRef, commandId, "holiday_saved")
                }
                "delete_holiday" -> {
                    val id = snapshot.longVal("holidayId")
                        ?: error("holidayId required")
                    val holiday = app.database.holidayDao().getById(id)
                    if (holiday != null) app.database.holidayDao().delete(holiday)
                    BellScheduler(app).rescheduleAll()
                    publishState(deviceId)
                    publishPresence(deviceId)
                    ackOk(ackRef, commandId, "holiday_deleted")
                }
                "import_track" -> {
                    val name = snapshot.child("name").getValue(String::class.java) ?: "utwor.mp3"
                    val url = snapshot.child("url").getValue(String::class.java)
                    val b64 = snapshot.child("base64").getValue(String::class.java)
                    val file = when {
                        !b64.isNullOrBlank() -> TrackStorage.importFromBase64(app, b64, name)
                        !url.isNullOrBlank() -> TrackStorage.importFromUrl(app, url, name)
                        else -> error("url or base64 required")
                    }
                    app.database.trackDao().insert(
                        TrackEntity(name = name, filePath = file.absolutePath)
                    )
                    publishState(deviceId)
                    ackOk(ackRef, commandId, "track_imported")
                }
                "delete_track" -> {
                    val id = snapshot.longVal("trackId")
                        ?: error("trackId required")
                    val track = app.database.trackDao().getById(id)
                    if (track != null) {
                        app.database.trackDao().clearTrackRefs(track.id)
                        app.database.trackDao().delete(track)
                        File(track.filePath).delete()
                    }
                    publishState(deviceId)
                    ackOk(ackRef, commandId, "track_deleted")
                }
                "refresh_state" -> {
                    publishState(deviceId)
                    publishPresence(deviceId)
                    ackOk(ackRef, commandId, "state_refreshed")
                }
                else -> ackOk(ackRef, commandId, "ignored_unknown_command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleCommand failed type=$type", e)
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

    private suspend fun ackOk(
        ackRef: com.google.firebase.database.DatabaseReference,
        commandId: String,
        status: String,
        extra: Map<String, Any?> = emptyMap()
    ) {
        val map = mutableMapOf<String, Any?>(
            "commandId" to commandId,
            "status" to status,
            "at" to ServerValue.TIMESTAMP
        )
        map.putAll(extra)
        ackRef.setValue(map).await()
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

    private fun DataSnapshot.longVal(key: String): Long? {
        val v = child(key).value ?: return null
        return when (v) {
            is Long -> v
            is Int -> v.toLong()
            is Double -> v.toLong()
            is Float -> v.toLong()
            is String -> v.toLongOrNull()
            else -> null
        }
    }

    private fun DataSnapshot.intVal(key: String): Int? = longVal(key)?.toInt()

    companion object {
        private const val TAG = "CommandListener"
    }
}
