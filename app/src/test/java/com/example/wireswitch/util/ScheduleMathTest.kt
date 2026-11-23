package com.example.wireswitch.util

import com.example.wireswitch.data.VpnSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleMathTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    // 2026-10-15 는 목요일.
    private fun at(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, seoul)

    private fun schedule(
        startHour: Int,
        endHour: Int,
        days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        paused: Boolean = false,
        id: Long = 1,
    ) = VpnSchedule(id, days, startHour, 0, endHour, 0, paused)

    @Test
    fun `시간창 안이면 켜짐 시작은 포함 종료는 제외`() {
        val schedules = listOf(schedule(9, 18))
        assertFalse(ScheduleMath.isActive(at(15, 8, 59), schedules))
        assertTrue(ScheduleMath.isActive(at(15, 9), schedules))
        assertTrue(ScheduleMath.isActive(at(15, 17, 59), schedules))
        assertFalse(ScheduleMath.isActive(at(15, 18), schedules))
    }

    @Test
    fun `일시정지이거나 요일이 비면 꺼짐`() {
        assertFalse(ScheduleMath.isActive(at(15, 10), listOf(schedule(9, 18, paused = true))))
        assertFalse(ScheduleMath.isActive(at(15, 10), listOf(schedule(9, 18, days = emptySet()))))
    }

    @Test
    fun `선택하지 않은 요일엔 꺼짐`() {
        val schedules = listOf(schedule(9, 18, days = setOf(DayOfWeek.MONDAY)))
        assertFalse(ScheduleMath.isActive(at(15, 10), schedules)) // 목요일
        assertTrue(ScheduleMath.isActive(at(19, 10), schedules)) // 월요일
    }

    @Test
    fun `자정을 넘는 시간창은 시작한 날의 요일 기준이다`() {
        val schedules = listOf(schedule(22, 6, days = setOf(DayOfWeek.THURSDAY)))
        assertTrue(ScheduleMath.isActive(at(15, 23), schedules)) // 목 23:00
        assertTrue(ScheduleMath.isActive(at(16, 5), schedules)) // 금 05:00 (목요일 일정의 연장)
        assertFalse(ScheduleMath.isActive(at(16, 6), schedules))
        assertFalse(ScheduleMath.isActive(at(15, 5), schedules)) // 목 05:00 은 수요일 일정이라 해당 없음
    }

    @Test
    fun `다음 경계는 가장 가까운 시작 또는 종료`() {
        val schedules = listOf(schedule(9, 18))
        assertEquals(at(15, 9), ScheduleMath.nextBoundary(at(15, 8), schedules))
        assertEquals(at(15, 18), ScheduleMath.nextBoundary(at(15, 10), schedules))
        assertEquals(at(16, 9), ScheduleMath.nextBoundary(at(15, 18), schedules))
    }

    @Test
    fun `동작할 일정이 없으면 다음 경계는 null`() {
        assertNull(ScheduleMath.nextBoundary(at(15, 8), emptyList()))
        assertNull(ScheduleMath.nextBoundary(at(15, 8), listOf(schedule(9, 18, paused = true))))
    }

    @Test
    fun `이어지는 일정의 경계에서도 켜짐이 유지된다`() {
        val schedules = listOf(schedule(9, 12, id = 1), schedule(12, 18, id = 2))
        assertTrue(ScheduleMath.isActive(at(15, 12), schedules))
    }

    @Test
    fun `nextEvent 는 경계 직후 상태를 돌려준다`() {
        val schedules = listOf(schedule(9, 18))
        assertEquals(at(15, 9) to true, ScheduleMath.nextEvent(at(15, 8), schedules))
        assertEquals(at(15, 18) to false, ScheduleMath.nextEvent(at(15, 10), schedules))
    }
}
