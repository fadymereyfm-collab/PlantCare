package com.example.plantcare.ui.home

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.plantcare.BuildConfig
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the redesigned Home tab (Roborazzi + Robolectric
 * native graphics). Renders [HomeSampleData] states, no DB or device needed.
 *
 *   Record references:  ./gradlew recordRoborazziDevDebug
 *   Check for changes:  ./gradlew verifyRoborazziDevDebug
 *
 * Reference PNGs: app/src/test/screenshots/home/.
 * A plain Application keeps App.onCreate (WorkManager, Ads, Firebase) out
 * of the test; German device because DE is the primary market and has the
 * longest strings.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "de-" + RobolectricDeviceQualifiers.Pixel7)
class HomeScreenScreenshotTest {

    /**
     * The Compose test activity comes from `debugImplementation ui-test-manifest`,
     * so release unit-test variants (run by `./gradlew test`) skip these tests
     * instead of failing. Ordered before [composeRule], which launches it.
     */
    @get:Rule(order = 0)
    val debugVariantOnly = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                assumeTrue("Screenshot tests run on debug variants only", BuildConfig.DEBUG)
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private fun capture(name: String, state: HomeUiState, dark: Boolean = false) {
        composeRule.setContent { HomeScreenSample(state, darkTheme = dark) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/home/$name.png")
    }

    @Test
    fun busy_light() = capture("busy_light", HomeSampleData.busyDay)

    @Test
    @Config(qualifiers = "+night")
    fun busy_dark() = capture("busy_dark", HomeSampleData.busyDay, dark = true)

    /** Whole page down to the challenges card. */
    @Test
    @Config(qualifiers = "de-w411dp-h1800dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    fun busy_light_fullPage() = capture("busy_light_full_page", HomeSampleData.busyDay)

    @Test
    fun empty_light() = capture("empty_light", HomeSampleData.empty)

    @Test
    @Config(qualifiers = "+night")
    fun vacation_dark() = capture("vacation_dark", HomeSampleData.vacation, dark = true)
}
