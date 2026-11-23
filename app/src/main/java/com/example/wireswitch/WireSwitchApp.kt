package com.example.wireswitch

import android.app.Application
import com.example.wireswitch.data.VpnSettingsStore
import com.example.wireswitch.work.Shortcuts
import com.example.wireswitch.work.VpnAlarmScheduler

class WireSwitchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 앱이 실행될 때마다 다음 시작/종료 알람을 복원한다.
        VpnAlarmScheduler.scheduleNext(this)
        Shortcuts.update(this, VpnSettingsStore(this).load().configs)
    }
}
