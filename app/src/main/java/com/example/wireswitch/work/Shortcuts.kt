package com.example.wireswitch.work

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.example.wireswitch.data.VpnConfig

/** 구성마다 "<터널> 연결" / "<터널> 해제" 바로가기를 런처에 등록한다(기기가 허용하는 개수까지). */
object Shortcuts {

    fun update(context: Context, configs: List<VpnConfig>) {
        val shortcuts = configs
            .filter { it.enabled && it.isConfigured }
            .flatMap { config ->
                listOf(
                    build(context, config, Automation.ACTION_CONNECT, "연결"),
                    build(context, config, Automation.ACTION_DISCONNECT, "해제"),
                )
            }
            .take(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))

        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
        catch (e: Exception) {
            // 바로가기는 부가 기능이라 실패해도 앱 동작엔 영향 없다.
            android.util.Log.w("Shortcuts", "바로가기 등록 실패", e)
        }
    }

    private fun build(context: Context, config: VpnConfig, action: String, verb: String): ShortcutInfoCompat {
        val intent = Intent(context, ShortcutActivity::class.java)
            .setAction(action)
            .putExtra(Automation.EXTRA_TUNNEL, config.tunnelName.trim())
        return ShortcutInfoCompat.Builder(context, "${config.id}_$verb")
            .setShortLabel("${config.title} $verb")
            .setLongLabel("${config.title} VPN $verb")
            .setIntent(intent)
            .build()
    }
}
