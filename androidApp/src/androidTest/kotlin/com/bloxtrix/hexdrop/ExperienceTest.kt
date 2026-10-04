package com.bloxtrix.hexdrop

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Device walkthrough in Turkish; screenshots land in the app's external files under experience-qa/. */
class ExperienceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    companion object {
        init {
            // Runs before the activity launches: a first-time Turkish player with no saved run.
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("ilmerya", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("turkish", true).putBoolean("music", false).putBoolean("sound", false).commit()
            context.getSharedPreferences("ilmerya_run", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "experience-qa").also { it.mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun back() = ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun menuTutorialPlayPauseResumeAndLeague() {
        capture("01-menu")
        ui.onNodeWithText("Ayarlar").performClick()
        ui.onNodeWithText("Taş ve arayüz sesleri").assertIsDisplayed()
        ui.onNodeWithText("Oyun müziği").assertIsDisplayed()
        capture("02-settings")
        ui.onNodeWithText("Tamam").performClick()

        ui.onNodeWithText("DİNGİN  ·  Kendi ritminde").performScrollTo().performClick()
        ui.onNodeWithText("İlk taşından önce").assertIsDisplayed()
        ui.onNodeWithText("Birleştir", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Yan yana üç adet 2, tek bir 4 olur. Çapraz komşular da sayılır.").assertIsDisplayed()
        capture("03-first-play-tutorial")
        ui.onNodeWithText("Başla").performClick()

        ui.onNodeWithText("↓  Yerleştir").assertIsDisplayed().performClick()
        ui.mainClock.advanceTimeBy(600)
        ui.onNodeWithContentDescription("Sütun 1").performClick()
        ui.onNodeWithText("↓  Yerleştir").performClick()
        ui.waitForIdle()
        capture("04-playing")

        back()
        ui.onNodeWithText("Bir nefes arası").assertIsDisplayed()
        capture("05-paused")
        ui.onNodeWithText("Ana menü").performClick()
        ui.onNodeWithText("Kaldığın yerden devam et").assertIsDisplayed()
        capture("06-menu-continue")

        ui.onNodeWithText("HAFTALIK LİG  ·  Sıralamanı gör").performScrollTo().performClick()
        ui.onNodeWithText("Haftalık lig").assertIsDisplayed()
        if (!BuildConfig.PGS_CONFIGURED) ui.onNodeWithText("Lig bu sürümde kapalı").assertIsDisplayed()
        ui.onAllNodesWithText("Lig hesabımı sil").assertCountEquals(if (BuildConfig.PGS_CONFIGURED) 0 else 0)
        capture("07-league")
        back()
        ui.onNodeWithText("Kaldığın yerden devam et").assertIsDisplayed().performClick()
        ui.onNodeWithText("Duraklat").assertIsDisplayed()
    }
}
