package com.example.plantcare.ui.settings

import android.app.Application
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.plantcare.BuildConfig
import com.example.plantcare.R
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot of the Settings layout (fragment_settings.xml) under the real
 * app theme, light and dark. Static layout only — the fragment's data
 * binding (name, e-mail, subscription) needs auth/billing and is not run.
 *
 * Reference PNGs: app/src/test/screenshots/settings/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "de-w411dp-h2000dp-normal-long-notround-any-420dpi-keyshidden-nonav")
class SettingsLayoutScreenshotTest {

    @Before
    fun debugVariantOnly() {
        // Same rule as the Compose screenshot tests: record/verify on debug only.
        assumeTrue("Screenshot tests run on debug variants only", BuildConfig.DEBUG)
    }

    private fun capture(name: String) {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_PlantCareApp)
        controller.setup()
        val view = LayoutInflater.from(activity).inflate(R.layout.fragment_settings, null, false)
        activity.setContentView(view)
        view.captureRoboImage("src/test/screenshots/settings/$name.png")
    }

    @Test
    fun settings_light() = capture("settings_light")

    @Test
    @Config(qualifiers = "+night")
    fun settings_dark() = capture("settings_dark")
}
