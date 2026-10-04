package com.bloxtrix.hexdrop

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runners.MethodSorters
import org.junit.Test

/** Device walkthrough in Turkish. Screenshots go to /sdcard/Pictures/ilmerya-qa and survive uninstall. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
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

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }
    private fun capture(name: String) {
        ui.waitForIdle()
        Thread.sleep(400)
        shell("mkdir -p /sdcard/Pictures/ilmerya-qa")
        shell("screencap -p /sdcard/Pictures/ilmerya-qa/$name.png")
        Thread.sleep(600)
    }
    private fun back() = ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
    private fun place(times: Int) = repeat(times) { i ->
        ui.onNodeWithContentDescription("Sütun ${i % 5 + 1}").performClick()
        ui.onNodeWithText("↓  Yerleştir").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("↓  Yerleştir").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(1200) // let the cascade animation finish so the next tap is accepted
    }

    @Test fun a_firstLaunchTutorialPlayPauseResumeAndLeague() {
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

        place(9)
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
        capture("07-league")
        back()
        ui.onNodeWithText("Kaldığın yerden devam et").assertIsDisplayed().performClick()
        ui.onNodeWithText("Duraklat").assertIsDisplayed()
    }

    /** Starts a Calm run whatever earlier tests left behind (tutorial seen, unfinished run). */
    private fun startCalm() {
        ui.onNodeWithText("DİNGİN  ·  Kendi ritminde").performScrollTo().performClick()
        ui.waitForIdle()
        if (ui.onAllNodesWithText("Yeni oyun başlat").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Yeni oyun başlat").performClick()
        ui.waitForIdle()
        if (ui.onAllNodesWithText("Başla").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Başla").performClick()
    }

    @Test fun b_boardAndSettingsSurviveActivityRecreation() {
        startCalm()
        place(4)
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        // The game screen and its board are restored; the run is not lost on configuration change.
        ui.onNodeWithText("Duraklat").assertIsDisplayed()
        ui.onNodeWithText("Duraklat").performClick()
        ui.onNodeWithText("Ayarlar").performClick()
        ui.onNodeWithText("English").performClick()
        ui.onNodeWithText("Done").performClick()
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithText("Take a breath").assertIsDisplayed()
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Türkçe").performClick()
        ui.onNodeWithText("Tamam").performClick()
    }
}
