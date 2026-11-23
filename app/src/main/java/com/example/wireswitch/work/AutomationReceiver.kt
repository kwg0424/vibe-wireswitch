package com.example.wireswitch.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 외부 자동화가 보내는 com.wirepilot.CONNECT / DISCONNECT / TOGGLE 를 받는다. UI 를 띄우지 않는다.
 *
 * exported=true 여야 다른 앱이 보낼 수 있으므로 무단 호출 방어는 [Automation.handle]에서 한다
 * (설정의 외부 제어 허용 스위치, 사용자가 고른 VPN 만). 매니페스트 리시버라
 * 보내는 쪽이 이 앱 패키지를 명시한 인텐트여야 Android 8+ 에서 전달된다.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 보낸 앱 패키지는 Android 14+ 에서만 알 수 있다. 이전 버전에선 null.
        val caller = if (Build.VERSION.SDK_INT >= 34) sentFromPackage else null
        val action = intent.action
        val tunnel = intent.getStringExtra(Automation.EXTRA_TUNNEL)
        val appContext = context.applicationContext
        // VPN 연결은 끝날 때까지 막히므로 백그라운드 스레드에서 처리하고, 끝날 때까지 프로세스를 유지한다.
        BackgroundWork.run(goAsync()) {
            Automation.handle(
                context = appContext,
                action = action,
                tunnel = tunnel,
                trusted = false,
                caller = caller,
            )
        }
    }
}
