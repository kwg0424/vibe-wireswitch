package com.example.wireswitch.data

import android.content.Context
import java.time.DayOfWeek
import org.json.JSONArray
import org.json.JSONObject

/**
 * VPN 을 켜 둘 시간대 하나. [days] 요일의 [startHour]:[startMinute] 에 켜고 [endHour]:[endMinute] 에 끈다.
 * 종료가 시작보다 이르거나 같으면 다음 날 종료로 본다(예: 22:00~06:00). [paused]면 삭제하지 않고 건너뛴다.
 */
data class VpnSchedule(
    val id: Long,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 18,
    val endMinute: Int = 0,
    val paused: Boolean = false,
    /** 이 일정이 켜고 끌 VPN 구성([VpnConfig.id]). */
    val configId: Long = 0,
) {
    /** 일시정지가 아니고 요일이 하나라도 골라져 있어야 실제로 동작한다. */
    val isActive: Boolean
        get() = !paused && days.isNotEmpty()

    /** 종료 시각이 시작 시각과 같거나 이르면 다음 날로 넘어가는 시간대. */
    val crossesMidnight: Boolean
        get() = endHour * 60 + endMinute <= startHour * 60 + startMinute
}

/**
 * 이 앱이 직접 연결하는 WireGuard 구성 하나. 일정과는 무관하게 추가·삭제되며,
 * 일정([VpnSchedule.configId])이 이 구성을 고르면 그 일정에 따라 자동 제어된다.
 * 설정 파일(개인키 포함) 전문은 여기 두지 않고 [SecretStore] 에 암호화해 보관한다.
 */
data class VpnConfig(
    val id: Long,
    /**
     * 구성 이름 — 설정 파일명(.conf 제외) 또는 입력한 이름. 화면·바로가기에 보이는 이름이자
     * 외부 명령의 "tunnel" extra 와 맞춰 보는 이름이다. 다른 구성과 겹칠 수 없다.
     */
    val tunnelName: String = "",
    /** 이 구성을 쓸지 여부. 꺼져 있으면 일정·자동화 모두 이 구성을 건드리지 않는다. */
    val enabled: Boolean = true,
    /** 설정 파일을 가져와 [SecretStore] 에 저장했는가. 아니면 연결할 수 없다. */
    val imported: Boolean = false,
) {
    val isConfigured: Boolean
        get() = imported

    /** 화면에 보여줄 이름. */
    val title: String
        get() = tunnelName.trim()
}

data class VpnSettings(
    /** 전체 기능 켜기/끄기. 꺼져 있으면 예약된 자동 제어를 하지 않는다. */
    val enabled: Boolean = true,
    val configs: List<VpnConfig> = emptyList(),
    /** 외부 앱(삼성 루틴 등)의 인텐트 제어 허용. 기본 꺼짐 — 켜야만 외부 명령을 받는다. */
    val automationEnabled: Boolean = false,
    /** 외부 명령으로 켜고 끌 수 있는 VPN 구성 id. 여기 없는 구성은 외부에서 제어할 수 없다. */
    val automationTargetIds: Set<Long> = emptySet(),
    /** 요일·시간 일정 전체. 각 일정이 [VpnSchedule.configId] 로 켜고 끌 VPN 을 고른다. */
    val schedules: List<VpnSchedule> = emptyList(),
) {
    fun schedulesOf(configId: Long): List<VpnSchedule> = schedules.filter { it.configId == configId }

    /** 일정으로 자동 제어되는 구성인가(일시정지된 일정도 "일정이 있음"으로 본다). */
    fun isScheduled(config: VpnConfig): Boolean =
        config.enabled && config.isConfigured && schedules.any { it.configId == config.id }

    /** 자동 제어 대상 구성의 일정만 — 알람 계산용. */
    val activeSchedules: List<VpnSchedule>
        get() = schedules.filter { schedule -> configs.any { it.id == schedule.configId && isScheduled(it) } }
}

class VpnSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): VpnSettings = VpnSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        configs = parseConfigs(prefs.getString(KEY_CONFIGS, null)),
        automationEnabled = prefs.getBoolean(KEY_AUTOMATION_ENABLED, false),
        automationTargetIds = prefs.getString(KEY_AUTOMATION_TARGETS, "").orEmpty()
            .split(",").mapNotNull { it.trim().toLongOrNull() }.toSet(),
        schedules = parseSchedules(prefs.getString(KEY_SCHEDULES, null)?.let { JSONArray(it) }),
    )

    fun save(settings: VpnSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_CONFIGS, serializeConfigs(settings.configs))
            .putBoolean(KEY_AUTOMATION_ENABLED, settings.automationEnabled)
            .putString(KEY_AUTOMATION_TARGETS, settings.automationTargetIds.joinToString(","))
            .putString(KEY_SCHEDULES, serializeSchedules(settings.schedules).toString())
            .apply()
    }

    /** 구성별로 마지막에 WireGuard 에 보낸 상태(true=켜기, false=끄기). 한 번도 안 보냈으면 null. */
    fun lastSentUp(configId: Long): Boolean? {
        val key = KEY_LAST_SENT_UP + configId
        return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    }

    fun setLastSentUp(configId: Long, up: Boolean) {
        prefs.edit().putBoolean(KEY_LAST_SENT_UP + configId, up).apply()
    }

    /** 화면에 보여줄 구성별 마지막 전송 기록(시각 + 결과). */
    fun lastEvent(configId: Long): String = prefs.getString(KEY_LAST_EVENT + configId, "").orEmpty()

    fun setLastEvent(configId: Long, value: String) {
        prefs.edit().putString(KEY_LAST_EVENT + configId, value).apply()
    }

    private fun serializeConfigs(configs: List<VpnConfig>): String {
        val array = JSONArray()
        configs.forEach { config ->
            array.put(
                JSONObject()
                    .put("id", config.id)
                    .put("tunnelName", config.tunnelName.trim())
                    .put("enabled", config.enabled)
                    .put("imported", config.imported),
            )
        }
        return array.toString()
    }

    private fun parseConfigs(raw: String?): List<VpnConfig> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }

        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    VpnConfig(
                        id = obj.getLong("id"),
                        // 이전 버전에서 따로 붙였던 표시 이름이 있으면 그것을 구성 이름으로 쓴다.
                        tunnelName = obj.optString("displayName", "").ifBlank { obj.optString("tunnelName", "") },
                        enabled = obj.optBoolean("enabled", true),
                        imported = obj.optBoolean("imported", false),
                    ),
                )
            }
        }
    }

    private fun serializeSchedules(schedules: List<VpnSchedule>): JSONArray {
        val array = JSONArray()
        schedules.forEach { schedule ->
            array.put(
                JSONObject()
                    .put("id", schedule.id)
                    .put("days", serializeDays(schedule.days))
                    .put("startHour", schedule.startHour)
                    .put("startMinute", schedule.startMinute)
                    .put("endHour", schedule.endHour)
                    .put("endMinute", schedule.endMinute)
                    .put("paused", schedule.paused)
                    .put("configId", schedule.configId),
            )
        }
        return array
    }

    private fun parseSchedules(array: JSONArray?): List<VpnSchedule> {
        if (array == null) {
            return emptyList()
        }
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    VpnSchedule(
                        id = obj.getLong("id"),
                        days = parseDays(obj.getInt("days")),
                        startHour = obj.getInt("startHour"),
                        startMinute = obj.getInt("startMinute"),
                        endHour = obj.getInt("endHour"),
                        endMinute = obj.getInt("endMinute"),
                        paused = obj.optBoolean("paused", false),
                        configId = obj.optLong("configId", 0),
                    ),
                )
            }
        }
    }

    /** 요일 하나당 비트 하나(월=bit0 ... 일=bit6)로 저장한다. */
    private fun serializeDays(days: Set<DayOfWeek>): Int =
        days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    private fun parseDays(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filterTo(mutableSetOf()) { day -> (mask and (1 shl (day.value - 1))) != 0 }

    companion object {
        private const val PREFS_NAME = "vpn_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_CONFIGS = "configs"
        private const val KEY_SCHEDULES = "schedules"
        private const val KEY_AUTOMATION_ENABLED = "automation_enabled"
        private const val KEY_AUTOMATION_TARGETS = "automation_targets"
        private const val KEY_LAST_SENT_UP = "last_sent_up_"
        private const val KEY_LAST_EVENT = "last_event_"
    }
}
