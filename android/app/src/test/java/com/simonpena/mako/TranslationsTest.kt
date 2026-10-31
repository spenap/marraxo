package com.simonpena.mako

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** Every string as this configuration shows it, by name. */
    private fun shown(): Map<String, String> = strings("values").keys
        .mapNotNull { name -> res.getIdentifier(name, "string", "com.simonpena.mako").takeIf { it != 0 }?.let { name to res.getString(it) } }
        .toMap()

    private fun assertNoPhone(word: Regex) {
        val phone = shown().filterValues { word.containsMatchIn(it) }
        assertTrue("say tablet on a tablet: $phone", phone.isEmpty())
    }

    @Config(qualifiers = "sw600dp")
    @Test fun `on a tablet it says tablet, in English`() {
        assertNoPhone(Regex("phone", RegexOption.IGNORE_CASE))
        assertEquals("Your music, on this tablet.", res.getString(R.string.welcome_title))
        assertEquals("This tablet", res.getString(R.string.this_phone))
    }

    @Config(qualifiers = "es-sw600dp")
    @Test fun `on a tablet it says tablet, in Spanish`() {
        assertNoPhone(Regex("teléfono", RegexOption.IGNORE_CASE))
        assertEquals("Tu música, en esta tableta.", res.getString(R.string.welcome_title))
        assertEquals("Ocultos en la biblioteca. Los archivos siguen en la tableta.", res.getString(R.string.hidden_some))
    }

    @Config(qualifiers = "gl-sw600dp")
    @Test fun `on a tablet it says tablet, in Galician`() {
        assertNoPhone(Regex("teléfono", RegexOption.IGNORE_CASE))
        assertEquals("Copia música no cartafol Music desta tableta, por transferencia de ficheiros USB ou desde un ordenador:", res.getString(R.string.empty_body))
    }

    @Test fun `on a phone it still says phone`() {
        assertEquals("This phone", res.getString(R.string.this_phone))
    }

    @Test fun `why the radio picked a song, in English`() {
        assertEquals("Hugh Padgham on both", reasonText(res, Reason.Shares("Hugh Padgham")))
        assertEquals("Celtic", reasonText(res, Reason.Genre("Celtic")))
        assertEquals("1980s", reasonText(res, Reason.Decade(1980)))
        assertEquals("More Altan", reasonText(res, Reason.Artist("Altan")))
        assertEquals("Further afield", reasonText(res, Reason.Wider))
        assertEquals("Radio · Same era", res.getString(R.string.radio_now, res.getString(RadioMode.Era.label)))
    }

    @Config(qualifiers = "gl")
    @Test fun `why the radio picked a song, in Galician`() {
        assertEquals("Hugh Padgham nos dous", reasonText(res, Reason.Shares("Hugh Padgham")))
        assertEquals("Década de 1980", reasonText(res, Reason.Decade(1980)))
        assertEquals("Mesma xente", res.getString(RadioMode.People.label))
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
