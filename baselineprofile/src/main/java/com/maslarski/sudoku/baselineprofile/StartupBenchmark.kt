package com.maslarski.sudoku.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    private val macrobenchmarkRule = MacrobenchmarkRule()

    @get:Rule
    val benchmarkRule: TestRule = TestRule { base, description ->
        val enabledRules = InstrumentationRegistry.getArguments()
            .getString("androidx.benchmark.enabledRules")
            .orEmpty()
            .split(',')
        if (enabledRules.any { it.trim().equals("Macrobenchmark", ignoreCase = true) }) {
            macrobenchmarkRule.apply(base, description)
        } else {
            object : Statement() {
                override fun evaluate() = Unit
            }
        }
    }

    @Test
    fun coldStartupWithoutCompilation() = macrobenchmarkRule.measureRepeated(
        packageName = "com.maslarski.sudoku",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 10,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun coldStartupWithBaselineProfile() = macrobenchmarkRule.measureRepeated(
        packageName = "com.maslarski.sudoku",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.COLD,
        iterations = 10,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
