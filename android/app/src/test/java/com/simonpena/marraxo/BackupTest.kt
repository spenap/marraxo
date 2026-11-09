package com.simonpena.marraxo

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/** Play history and the rest of the app's data never go to a cloud backup. */
@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun noBackupUpToAndroid11() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    /** Android 12+: the cloud backup excludes every domain; a direct phone-to-phone transfer keeps them. */
    @Test fun cloudBackupExcludesEverything() {
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        var section: String? = null
        val excluded = mutableMapOf<String, MutableSet<String>>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer" -> section = parser.name
                "exclude" -> excluded.getOrPut(section!!) { mutableSetOf() } +=
                    parser.getAttributeValue(null, "domain") + ":" + parser.getAttributeValue(null, "path")
            }
        }
        val all = setOf("root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref").map { "$it:." }.toSet()
        assertEquals(all, excluded["cloud-backup"])
        assertTrue(excluded["device-transfer"].isNullOrEmpty())
    }
}
