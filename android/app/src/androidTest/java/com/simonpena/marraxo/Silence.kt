package com.simonpena.marraxo

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Two minutes of silence that the player can really play: a fake URI would now be skipped as unplayable. */
val silence: Uri by lazy {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val file = File(instrumentation.targetContext.cacheDir, "silence.mp3")
    instrumentation.context.assets.open("silence.mp3").use { input -> file.outputStream().use { input.copyTo(it) } }
    Uri.fromFile(file)
}
