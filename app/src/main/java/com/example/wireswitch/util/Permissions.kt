package com.example.wireswitch.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object Permissions {

    /**
     * 정확한 알람 예약 권한. Android 12+ 는 설정 화면에서 사용자가 직접 허용해야 한다.
     * 거부된 상태면 [com.example.wireswitch.work.VpnAlarmScheduler]가 근사 알람으로 대신 예약한다.
     */
    fun hasExactAlarmAccess(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun exactAlarmSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
}
