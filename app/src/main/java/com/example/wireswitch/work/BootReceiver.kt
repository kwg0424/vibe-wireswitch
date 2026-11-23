package com.example.wireswitch.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 재부팅/앱 업데이트/시각·시간대 변경/정확한 알람 권한 변경 후 알람을 복원하고 현재 상태를 맞춘다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        BackgroundWork.run(goAsync()) { VpnController.sync(appContext) }
    }
}
