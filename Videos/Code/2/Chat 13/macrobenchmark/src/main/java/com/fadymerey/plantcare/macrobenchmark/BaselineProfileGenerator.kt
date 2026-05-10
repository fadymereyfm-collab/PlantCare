package com.fadymerey.plantcare.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = "com.fadymerey.plantcare") {
            pressHome()
            startActivityAndWait()
            // Onboarding → plant list navigation covers the critical startup path.
            // Add more interactions here as the app grows to capture deeper screens.
        }
    }
}
