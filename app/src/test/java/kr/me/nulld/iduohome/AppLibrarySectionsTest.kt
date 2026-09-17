package kr.me.nulld.iduohome

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLibrarySectionsTest {
    @Test fun koreanAppsGroupByInitialInsteadOfWholeSyllable() {
        val apps = listOf("갤러리", "게임", "네이버", "녹음", "다음", "지도", "카메라", "힣")
        val groups = apps.groupBy(::appLibrarySection)
        assertEquals(listOf("ㄱ", "ㄴ", "ㄷ", "ㅈ", "ㅋ", "ㅎ"), groups.keys.toList())
        assertEquals(listOf("갤러리", "게임"), groups["ㄱ"])
        assertEquals(listOf("네이버", "녹음"), groups["ㄴ"])
        assertEquals("ㄲ", appLibrarySection("꼬마"))
        assertEquals("ㄱ", appLibrarySection("\u1100\u1161"))
        assertEquals("ㄱ", appLibrarySection("ㄱ 앱"))
    }

    @Test fun latinAndOtherAppSectionsKeepExistingBehavior() {
        assertEquals("C", appLibrarySection("chrome"))
        assertEquals("C", appLibrarySection("Chrome"))
        assertEquals("#", appLibrarySection("123 앱"))
        assertEquals("#", appLibrarySection(""))
        assertEquals("#", appLibrarySection("★ 앱"))
        assertEquals("地", appLibrarySection("地图"))
    }
}
