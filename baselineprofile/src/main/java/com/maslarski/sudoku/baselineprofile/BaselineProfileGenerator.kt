package com.maslarski.sudoku.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.maslarski.sudoku",
        includeInStartupProfile = true,
    ) {
        pressHome()
        device.executeShellCommand("pm clear com.maslarski.sudoku")
        startActivityAndWait()

        device.requireText("home", "Pick a grid size and difficulty")
        device.scrollToText("9×9", "grid-size")
            .click()
        device.scrollToText("Easy", "difficulty")
            .click()
        device.scrollToText("New game", "new-game")
            .click()

        if (device.wait(Until.findObject(By.text("Replace saved game?")), 3_000) != null) {
            device.requireText("replace-saved-game", "Start new")
                .click()
        }

        device.requireObject("game-board", By.desc("Pause"), 60_000)
        val notes = device.requireObject("notes-control", By.desc("Notes"), 20_000)
        val pause = device.requireObject("pause-control", By.desc("Pause"), 2_000)
        val statusBottom = listOf(
            By.textContains("Mistakes:"),
            By.textContains("Moves:"),
            By.textContains("Score:"),
            By.textContains(":"),
        ).mapNotNull { device.findObject(it)?.visibleBounds?.bottom }.maxOrNull()
            ?: device.failStep("status-row")
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        val spacing = (12 * density).roundToInt()
        val boardTop = statusBottom + spacing
        val boardBottom = notes.visibleBounds.top - spacing
        val boardSize = minOf(device.displayWidth - spacing * 2, boardBottom - boardTop)
        if (pause.visibleBounds.bottom >= boardTop || boardSize <= 0) device.failStep("board-bounds")

        val boardLeft = (device.displayWidth - boardSize) / 2
        val cellSize = boardSize / 9f
        repeat(5) { index ->
            val row = (index * 2 + 1) % 9
            val column = (index * 3 + 2) % 9
            device.click(
                (boardLeft + (column + 0.5f) * cellSize).roundToInt(),
                (boardTop + (row + 0.5f) * cellSize).roundToInt(),
            )
        }
        notes.click()
        device.findObjects(By.text("1"))
            .filter { it.visibleBounds.top > notes.visibleBounds.bottom }
            .maxByOrNull { it.visibleBounds.top }
            ?.click()
            ?: device.failStep("number-key")

        pressBack()
        device.requireText("home-after-game", "Pick a grid size and difficulty")
        device.requireObject("leaderboard-navigation", By.desc("Leaderboard"))
            .click()
        device.requireText("leaderboard", "Leaderboard")
        pressBack()
        device.requireText("home-before-settings", "Pick a grid size and difficulty")
        device.requireObject("settings-navigation", By.desc("Settings"))
            .click()
        device.requireText("settings", "Your name")
    }
}

private fun UiDevice.requireText(step: String, text: String, timeout: Long = 20_000): UiObject2 =
    requireObject(step, By.text(text), timeout)

private fun UiDevice.requireObject(step: String, selector: androidx.test.uiautomator.BySelector, timeout: Long = 20_000): UiObject2 =
    wait(Until.findObject(selector), timeout) ?: failStep(step)

private fun UiDevice.scrollToText(text: String, step: String): UiObject2 {
    wait(Until.findObject(By.text(text)), 15_000)?.let { return it }
    val scrollable = wait(Until.findObject(By.scrollable(true)), 5_000) ?: failStep("$step-scroll-container")
    return scrollable.scrollUntil(Direction.DOWN, Until.findObject(By.text(text)))
        ?: failStep(step)
}

private fun UiDevice.failStep(step: String): Nothing {
    val filename = "bp-fail-$step"
    val xmlPath = "/sdcard/Download/$filename.xml"
    val screenshotPath = "/sdcard/Download/$filename.png"
    executeShellCommand("mkdir -p /sdcard/Download")
    runCatching { dumpWindowHierarchy(File(xmlPath)) }
    executeShellCommand("uiautomator dump $xmlPath")
    executeShellCommand("screencap -p $screenshotPath")
    error("Baseline profile journey failed at $step; diagnostics saved to $xmlPath and $screenshotPath")
}
