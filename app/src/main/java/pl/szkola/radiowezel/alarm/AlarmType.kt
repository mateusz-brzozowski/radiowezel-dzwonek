package pl.szkola.radiowezel.alarm

enum class AlarmType(
    val id: String,
    val label: String,
    val toneId: String,
    val voiceId: String,
    val drillVoiceId: String?,
    /** Max seconds for looping tone phase; null = until STOP */
    val maxSeconds: Int?
) {
    EVAC_FIRE(
        id = "evac_fire",
        label = "Pożar / ewakuacja",
        toneId = "tone_evacuate",
        voiceId = "voice_evac_fire",
        drillVoiceId = "voice_evac_drill",
        maxSeconds = 180
    ),
    BOMB(
        id = "bomb",
        label = "Alarm bombowy",
        toneId = "tone_bomb_chime",
        voiceId = "voice_bomb",
        drillVoiceId = "voice_bomb",
        maxSeconds = 180
    ),
    LOCKDOWN(
        id = "lockdown",
        label = "Blokada szkoły",
        toneId = "tone_lockdown",
        voiceId = "voice_lockdown",
        drillVoiceId = "voice_lockdown",
        maxSeconds = null
    ),
    SHELTER(
        id = "shelter",
        label = "Schronienie w budynku",
        toneId = "tone_shelter",
        voiceId = "voice_shelter",
        drillVoiceId = "voice_shelter",
        maxSeconds = 180
    ),
    AIR_RAID(
        id = "air_raid",
        label = "Alarm powietrzny",
        toneId = "tone_modulated_announce",
        voiceId = "voice_air_raid",
        drillVoiceId = "voice_air_raid",
        maxSeconds = 180
    ),
    ALL_CLEAR(
        id = "all_clear",
        label = "Odwołanie alarmu",
        toneId = "tone_continuous_clear",
        voiceId = "voice_all_clear",
        drillVoiceId = "voice_all_clear",
        maxSeconds = 60
    ),
    TEST(
        id = "test",
        label = "Test systemu",
        toneId = "tone_evacuate",
        voiceId = "voice_test_short",
        drillVoiceId = "voice_test_short",
        maxSeconds = 30
    );

    fun voiceFor(drill: Boolean): String =
        if (drill) (drillVoiceId ?: voiceId) else voiceId

    companion object {
        fun fromId(id: String): AlarmType? = entries.find { it.id == id }
    }
}
