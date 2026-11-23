package com.example.wireswitch.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class VpnAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val boundaryMillis = intent.getLongExtra(VpnAlarmScheduler.EXTRA_BOUNDARY_MILLIS, 0L)
        val now = ZonedDateTime.now()

        // 알람이 경계 시각보다 살짝 일찍 울려도 경계 직후의 상태로 판단한다(늦게 울리면 지금 시각 기준).
        val boundary = ZonedDateTime.ofInstant(Instant.ofEpochMilli(boundaryMillis), ZoneId.systemDefault())
            .plusSeconds(1)
        val at = if (boundaryMillis > 0 && boundary.isAfter(now)) boundary else now

        val appContext = context.applicationContext
        BackgroundWork.run(goAsync()) { VpnController.onAlarm(appContext, at) }
    }
}
