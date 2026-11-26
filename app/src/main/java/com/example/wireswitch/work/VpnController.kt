package com.example.wireswitch.work

import android.content.Context
import com.example.wireswitch.data.VpnConfig
import com.example.wireswitch.data.VpnSettingsStore
import com.example.wireswitch.util.ScheduleMath
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 일정에 맞는 VPN 상태를 계산해 [VpnEngine] 으로 켜고 끄고, 다음 알람을 다시 건다.
 * 연결이 끝날 때까지 막히므로 **모든 함수는 백그라운드 스레드에서 불러야 한다**([BackgroundWork]).
 */
object VpnController {

    private val EVENT_TIME_FORMAT = DateTimeFormatter.ofPattern("M/d HH:mm")

    /**
     * 알람이 울렸을 때(일정 시작/종료 경계 시각). [at] 시각의 상태를 일정 구성 전부에 맞춘다.
     * 일정에 따른 켜기/끄기는 오직 이 시각에만 일어난다.
     */
    fun onAlarm(context: Context, at: ZonedDateTime) {
        apply(context, at)
        VpnAlarmScheduler.scheduleNext(context)
    }

    /**
     * 부팅/앱 업데이트/설정 저장 직후. 연결 상태는 건드리지 않고 다음 알람만 다시 건다 —
     * 경계 시각이 아닐 때 수동으로 켠 VPN 이 일정 때문에 꺼지면 안 된다.
     */
    fun sync(context: Context) {
        VpnAlarmScheduler.scheduleNext(context)
    }

    private fun apply(context: Context, at: ZonedDateTime) {
        val store = VpnSettingsStore(context)
        val settings = store.load()
        if (!settings.enabled) {
            return
        }

        // 일정이 하나도 연결되지 않은 구성은 수동/자동화 전용이라 건드리지 않는다.
        val decisions = settings.configs
            .filter { settings.isScheduled(it) }
            .map { it to ScheduleMath.isActive(at, settings.schedulesOf(it.id)) }
            // 끄기를 먼저 보내고 켜기를 나중에 — 한 시각에 A 가 끝나고 B 가 시작해도 B 가 이긴다.
            .sortedBy { (_, up) -> up }

        decisions.forEach { (config, desiredUp) ->
            // 이미 원하는 상태면 다시 보내지 않는다(불필요한 재연결 방지).
            if (desiredUp != (VpnEngine.activeId == config.id)) {
                send(context, store, config, desiredUp, "${at.format(EVENT_TIME_FORMAT)} ${upLabel(desiredUp)}")
            }
        }
    }

    /** 지금 켜져 있는 구성. 엔진이 아는 실제 상태 기준이다. */
    fun activeConfigs(context: Context): List<VpnConfig> {
        val id = VpnEngine.activeId ?: return emptyList()
        return VpnSettingsStore(context).load().configs.filter { it.id == id }
    }

    /**
     * 첫 화면의 "연결 해제" 버튼용. 켜져 있는 VPN 을 끈다.
     * @return 끄기를 시도한 구성 수와 실패 수.
     */
    fun disconnectActive(context: Context): Pair<Int, Int> {
        // 구성 목록이 아니라 엔진이 아는 켜진 VPN 을 기준으로 끈다 — 켜 둔 채 구성을 삭제했어도 끊어져야 한다.
        val activeId = VpnEngine.activeId ?: return 0 to 0
        val result = VpnEngine.setUp(context, activeId, false)
        VpnSettingsStore(context).setLastEvent(activeId, "${now()} 연결 해제 · ${result.label}")
        return 1 to (if (result == VpnEngine.Result.OK) 0 else 1)
    }

    /** 화면에서 구성을 눌러 켜거나 끌 때. */
    fun manual(context: Context, config: VpnConfig, up: Boolean): VpnEngine.Result =
        send(context, VpnSettingsStore(context), config, up, "${now()} 수동 ${upLabel(up)}")

    /** 외부 자동화(루틴/바로가기) 명령용. [source] 는 기록에 남길 출처. */
    fun external(context: Context, config: VpnConfig, up: Boolean, source: String): VpnEngine.Result =
        send(context, VpnSettingsStore(context), config, up, "${now()} $source ${upLabel(up)}")

    private fun send(
        context: Context,
        store: VpnSettingsStore,
        config: VpnConfig,
        up: Boolean,
        label: String,
    ): VpnEngine.Result {
        val result = VpnEngine.setUp(context, config.id, up)
        store.setLastEvent(config.id, "$label · ${result.label}")
        return result
    }

    private fun now(): String = ZonedDateTime.now().format(EVENT_TIME_FORMAT)

    private fun upLabel(up: Boolean) = if (up) "켜기" else "끄기"
}
