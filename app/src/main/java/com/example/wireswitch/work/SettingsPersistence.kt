package com.example.wireswitch.work

import android.content.Context
import com.example.wireswitch.data.VpnSettings
import com.example.wireswitch.data.VpnSettingsStore

/**
 * 설정 저장 — 첫 화면과 설정 화면이 같은 방식으로 쓴다.
 * 저장 + 알람 재예약 + 일정에 맞는 상태로 조정 + 바로가기 갱신. 연결은 막히는 작업이라 백그라운드에서 한다.
 */
object SettingsPersistence {
    fun save(context: Context, settings: VpnSettings) {
        val app = context.applicationContext
        VpnSettingsStore(app).save(settings)
        BackgroundWork.run { VpnController.sync(app) }
        Shortcuts.update(app, settings.configs)
    }
}
