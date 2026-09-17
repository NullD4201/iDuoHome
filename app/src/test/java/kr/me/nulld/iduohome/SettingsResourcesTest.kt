package kr.me.nulld.iduohome

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class SettingsResourcesTest {
    @Test fun koreanSettingsHaveEveryKeyAndMatchingFormatArguments() {
        fun entries(locale: String): Map<String, List<String>> {
            val file = File("src/main/res/$locale/settings.xml")
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val children = doc.documentElement.childNodes
            val result = linkedMapOf<String, List<String>>()
            for (i in 0 until children.length) {
                val element = children.item(i) as? Element ?: continue
                val key = element.getAttribute("name")
                assertFalse("Duplicate translation: $key", result.containsKey(key))
                result[key] = if (element.tagName == "plurals") {
                    val items = element.getElementsByTagName("item")
                    (0 until items.length).map { items.item(it).textContent }
                } else listOf(element.textContent)
            }
            return result
        }
        val english = entries("values")
        val korean = entries("values-ko")
        assertTrue(english.size > 290)
        assertEquals("Missing or unexpected Korean keys", english.keys, korean.keys)
        val argument = Regex("%(\\d+\\$)[sdf]")
        for ((key, translations) in english) {
            val expected = argument.findAll(translations.first()).map { it.value }.sorted().toList()
            for (text in translations + korean.getValue(key)) {
                assertTrue("Blank translation: $key", text.isNotBlank())
                assertEquals("Format arguments: $key", expected, argument.findAll(text).map { it.value }.sorted().toList())
            }
        }
    }
}
