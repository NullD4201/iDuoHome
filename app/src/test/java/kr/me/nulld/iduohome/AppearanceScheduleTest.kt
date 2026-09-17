package kr.me.nulld.iduohome

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AppearanceScheduleTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val date = LocalDate.of(2026, 9, 14)
    private val location = AppearanceState(latitude = 37.5665, longitude = 126.9780)

    @Test fun backgroundChangesAtSunriseAndSunsetRegardlessOfAppTheme() {
        val schedule = solarSchedule(date, location.latitude!!, location.longitude!!, zone)
        val sunrise = schedule.sunrise!!; val sunset = schedule.sunset!!
        val cases = listOf(date.atStartOfDay(zone) to true, sunrise.minusSeconds(1) to true,
            sunrise to false, sunset.minusSeconds(1) to false, sunset to true,
            date.plusDays(1).atStartOfDay(zone) to true)
        for ((time, expectedNight) in cases) {
            for (mode in AppearanceMode.entries) {
                for (systemDark in listOf(false, true)) {
                    val result = resolveAppearance(location.copy(mode = mode), systemDark, time)
                    assertEquals("Background at $time in $mode", expectedNight, result.backgroundDark)
                    assertNull(result.fallback)
                    assertEquals(when (mode) {
                        AppearanceMode.LIGHT -> false
                        AppearanceMode.DARK -> true
                        AppearanceMode.SYSTEM -> systemDark
                        AppearanceMode.SUNRISE_SUNSET -> expectedNight
                    }, result.dark)
                }
            }
        }
    }

    @Test fun missingInvalidAndStaleLocationsUseSystemBackgroundWithoutOverridingAppTheme() {
        val now = date.atTime(12, 0).atZone(zone)
        val cases = listOf(AppearanceState(), location.copy(latitude = Double.NaN),
            location.copy(deviceLocation = true, locationTime = now.minusDays(31).toInstant().toEpochMilli()))
        for (value in cases) {
            val result = resolveAppearance(value.copy(mode = AppearanceMode.LIGHT), systemDark = true, now)
            assertFalse(result.dark)
            assertTrue(result.backgroundDark)
            assertNotNull(result.fallback)
        }
    }

    @Test fun polarDayAndNightSelectTheirCorrespondingBackgrounds() {
        val value = AppearanceState(mode = AppearanceMode.LIGHT, latitude = 78.2, longitude = 15.6)
        val zone = ZoneId.of("Arctic/Longyearbyen")
        assertFalse(resolveAppearance(value, true, LocalDate.of(2026, 6, 21).atStartOfDay(zone)).backgroundDark)
        assertTrue(resolveAppearance(value, false, LocalDate.of(2026, 12, 21).atTime(12, 0).atZone(zone)).backgroundDark)
    }
}
