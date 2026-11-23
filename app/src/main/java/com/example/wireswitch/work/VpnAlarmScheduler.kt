package com.example.wireswitch.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.wireswitch.data.VpnSettingsStore
import com.example.wireswitch.util.ScheduleMath
import java.time.ZonedDateTime

/** 일정들 중 가장 가까운 시작/종료 시각에 [VpnAlarmReceiver]를 깨우는 알람을 예약한다. */
object VpnAlarmScheduler {

    private const val TAG = "VpnAlarmScheduler"
    private const val REQUEST_CODE = 1
    const val EXTRA_BOUNDARY_MILLIS = "boundary_millis"

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val settings = VpnSettingsStore(context).load()

        val next = if (settings.enabled) {
            ScheduleMath.nextBoundary(ZonedDateTime.now(), settings.activeSchedules)
        }
        else {
            null
        }
        if (next == null) {
            Log.i(TAG, "기능이 꺼져 있거나 동작할 일정이 없어 알람을 취소합니다")
            alarmManager.cancel(pendingIntent(context, 0L))
            return
        }

        val triggerAt = next.toInstant().toEpochMilli()
        val pendingIntent = pendingIntent(context, triggerAt)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
        else {
            Log.w(TAG, "정확한 알람 권한이 없어 근사 알람으로 예약합니다 — 정시보다 늦을 수 있습니다")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun pendingIntent(context: Context, boundaryMillis: Long): PendingIntent {
        val intent = Intent(context, VpnAlarmReceiver::class.java)
            .putExtra(EXTRA_BOUNDARY_MILLIS, boundaryMillis)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
