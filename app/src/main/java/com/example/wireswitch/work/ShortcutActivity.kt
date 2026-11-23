package com.example.wireswitch.work

import android.app.Activity
import android.os.Bundle

/**
 * 앱 바로가기(길게 누르기 메뉴)용 화면 없는 진입점. 바로가기는 브로드캐스트를 직접 보낼 수 없어
 * 액티비티를 거치되, 화면을 그리지 않고 바로 끝낸다. exported=false 라 앱 내부(런처 바로가기)에서만 쓴다.
 */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.action
        val tunnel = intent.getStringExtra(Automation.EXTRA_TUNNEL)
        val appContext = applicationContext
        BackgroundWork.run {
            Automation.handle(
                context = appContext,
                action = action,
                tunnel = tunnel,
                trusted = true,
                caller = "shortcut",
            )
        }
        finish()
    }
}
