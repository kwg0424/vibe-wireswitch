package com.example.wireswitch.work

import android.content.Context
import android.util.Log
import com.example.wireswitch.data.VpnSettingsStore

/**
 * 외부 자동화(삼성 모드 및 루틴, Tasker 등)와 바로가기가 공유하는 명령 처리.
 * VPN 제어는 [VpnController] → [VpnEngine] 경로를 그대로 쓴다.
 */
object Automation {

    private const val TAG = "Automation"

    const val ACTION_CONNECT = "com.wirepilot.CONNECT"
    const val ACTION_DISCONNECT = "com.wirepilot.DISCONNECT"
    const val ACTION_TOGGLE = "com.wirepilot.TOGGLE"

    /** 제어할 터널 이름. 앱에 등록된 구성의 이름과 같아야 한다. */
    const val EXTRA_TUNNEL = "tunnel"

    /**
     * @param trusted true 면 앱 내부 경로(바로가기)라 "외부 제어 허용"·대상 선택 제한을 건너뛴다.
     * @param caller 호출한 패키지(알 수 있을 때만) — 기록과 로그용.
     * @return 처리 결과 설명(로그용). 거부되면 아무 것도 보내지 않는다.
     */
    fun handle(
        context: Context,
        action: String?,
        tunnel: String?,
        trusted: Boolean,
        caller: String?,
    ): String {
        val settings = VpnSettingsStore(context).load()

        if (!trusted && !settings.automationEnabled) {
            return reject("외부 제어가 꺼져 있음", action, caller)
        }
        if (!settings.enabled) {
            return reject("전체 기능이 꺼져 있음", action, caller)
        }

        // 외부 명령은 사용자가 설정에서 고른 VPN 하나만 제어할 수 있다.
        // 바로가기(trusted)는 구성마다 만들어져 tunnel extra 로 대상을 직접 지정한다.
        val candidates = settings.configs.filter {
            it.enabled && it.isConfigured && (trusted || it.id in settings.automationTargetIds)
        }
        val name = tunnel?.trim().orEmpty()
        // tunnel extra 가 없으면 고른 VPN 이 곧 대상이고, 있으면 그 VPN 의 터널 이름과 같아야 한다.
        val config = when {
            name.isNotEmpty() -> candidates.firstOrNull { it.tunnelName.trim() == name || it.title == name }
            !trusted -> candidates.firstOrNull()
            else -> null
        } ?: return reject("대상 VPN 을 찾지 못함(설정에서 고른 VPN 만 제어 가능)", action, caller)

        val up = when (action) {
            ACTION_CONNECT -> true
            ACTION_DISCONNECT -> false
            // 지금 실제로 켜져 있으면 끄고, 아니면 켠다.
            ACTION_TOGGLE -> VpnEngine.activeId != config.id
            else -> return reject("알 수 없는 action", action, caller)
        }

        val source = if (trusted) "바로가기" else "루틴"
        val result = VpnController.external(context, config, up, source)
        Log.i(TAG, "$action tunnel=${config.tunnelName} caller=$caller -> ${result.label}")
        return result.label
    }

    private fun reject(reason: String, action: String?, caller: String?): String {
        Log.w(TAG, "거부: $reason (action=$action caller=$caller)")
        return "거부: $reason"
    }
}
