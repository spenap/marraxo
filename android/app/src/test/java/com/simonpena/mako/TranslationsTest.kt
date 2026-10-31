package com.simonpena.mako

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Mako in English, Spanish and Galician: the phone's language picks one. */
@RunWith(RobolectricTestRunner::class)
class TranslationsTest {
    private val res: Resources get() = ApplicationProvider.getApplicationContext<android.app.Application>().resources

    /** Every string and plural by name, with its placeholders (%1$s, %d…) per form. */
    private fun strings(dir: String): Map<String, List<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
        val root = doc.documentElement
        val placeholders = { text: String -> Regex("%(\\d\\$)?[sd]").findAll(text).map { it.value }.sorted().joinToString() }
        return buildMap {
            val strings = root.getElementsByTagName("string")
            for (i in 0 until strings.length) strings.item(i).let { put(it.attributes.getNamedItem("name").nodeValue, listOf(placeholders(it.textContent))) }
            val plurals = root.getElementsByTagName("plurals")
            for (i in 0 until plurals.length) plurals.item(i).let { p ->
                // Forms differ by language; "other" must exist in each, with the same placeholders.
                val forms = (0 until p.childNodes.length).map { p.childNodes.item(it) }.filter { it.nodeName == "item" }
                val other = forms.first { it.attributes.getNamedItem("quantity").nodeValue == "other" }
                put(p.attributes.getNamedItem("name").nodeValue, listOf("plural", placeholders(other.textContent)))
            }
        }
    }

    @Test fun `every English string is translated to Spanish and Galician, with the same placeholders`() {
        val english = strings("values")
        for (language in listOf("values-es", "values-gl")) assertEquals(language, english, strings(language))
    }

    @Test fun `year labels in English`() {
        assertEquals("1990s", yearLabel(res, 1994, coarse = true))
        assertEquals("1994", yearLabel(res, 1994, coarse = false))
        assertEquals("2000s", yearLabel(res, 2002, coarse = true))
        assertEquals("Unknown year", yearLabel(res, 0, coarse = true))
        assertEquals("Unknown year", yearLabel(res, 0, coarse = false))
    }

    @Config(qualifiers = "es")
    @Test fun `year labels in Spanish`() {
        assertEquals("Década de 1990", yearLabel(res, 1994, coarse = true))
        assertEquals("Año desconocido", yearLabel(res, 0, coarse = false))
    }

    @Test fun `the rescan summary in English`() {
        assertEquals("Library is up to date", rescanSummary(res, ScanResult(0, 0)))
        assertEquals("Library updated: 3 files added, 1 file removed", rescanSummary(res, ScanResult(3, 1)))
        assertEquals("Rescan stopped early; rescan again to finish", rescanSummary(res, ScanResult(3, 0, complete = false)))
    }

    @Config(qualifiers = "gl")
    @Test fun `the rescan summary in Galician, with its plurals`() {
        assertEquals("Biblioteca actualizada: 3 ficheiros engadidos, 1 ficheiro eliminado", rescanSummary(res, ScanResult(3, 1)))
        assertEquals("Biblioteca actualizada: 1 ficheiro engadido", rescanSummary(res, ScanResult(1, 0)))
    }

    @Config(qualifiers = "es")
    @Test fun `the rescan summary in Spanish`() {
        assertEquals("Biblioteca actualizada: 2 archivos eliminados", rescanSummary(res, ScanResult(0, 2)))
    }
}
