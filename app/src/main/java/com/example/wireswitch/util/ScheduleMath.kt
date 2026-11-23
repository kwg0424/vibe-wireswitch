package com.example.wireswitch.util

import com.example.wireswitch.data.VpnSchedule
import java.time.ZonedDateTime

/**
 * 일정들로부터 "지금 VPN 이 켜져 있어야 하는가"와 "다음에 상태가 바뀔 수 있는 시각"을 계산한다.
 *
 * Android 의존성이 없는 순수 로직이라 JVM 단위 테스트 대상이다.
 * ([현재 시각][now] 을 인자로 받는 이유도 테스트에서 시각을 고정하기 위함)
 */
object ScheduleMath {

    /** 요일은 시작 시각이 속한 날 기준이다 — 금요일 22:00~06:00 이면 토요일 06:00 에 끝난다. */
    private data class Window(val start: ZonedDateTime, val end: ZonedDateTime)

    /** [now] 앞뒤로 겹칠 수 있는 모든 시간창(전날 시작~다음 주 시작). */
    private fun windows(now: ZonedDateTime, schedules: List<VpnSchedule>): List<Window> =
        schedules.filter { it.isActive }.flatMap { schedule ->
            (-1L..8L).mapNotNull { offset ->
                val date = now.toLocalDate().plusDays(offset)
                if (date.dayOfWeek !in schedule.days) {
                    return@mapNotNull null
                }
                val start = date.atTime(schedule.startHour, schedule.startMinute).atZone(now.zone)
                var end = date.atTime(schedule.endHour, schedule.endMinute).atZone(now.zone)
                if (!end.isAfter(start)) {
                    end = end.plusDays(1)
                }
                Window(start, end)
            }
        }

    /** [now] 가 어느 일정의 시간창 안(시작 포함, 종료 제외)이면 true. */
    fun isActive(now: ZonedDateTime, schedules: List<VpnSchedule>): Boolean =
        windows(now, schedules).any { !now.isBefore(it.start) && now.isBefore(it.end) }

    /** [now] 이후 가장 가까운 시작/종료 시각. 동작할 일정이 없으면 null. */
    fun nextBoundary(now: ZonedDateTime, schedules: List<VpnSchedule>): ZonedDateTime? =
        windows(now, schedules)
            .flatMap { listOf(it.start, it.end) }
            .filter { it.isAfter(now) }
            .minByOrNull { it.toInstant() }

    /** [nextBoundary] 와 그 시각 직후의 상태(true=켜짐). 화면 표시용. */
    fun nextEvent(now: ZonedDateTime, schedules: List<VpnSchedule>): Pair<ZonedDateTime, Boolean>? {
        val boundary = nextBoundary(now, schedules) ?: return null
        return boundary to isActive(boundary, schedules)
    }
}
