package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.Keyboard
import com.intellij.remoterobot.utils.waitFor
import java.awt.event.KeyEvent
import java.time.Duration

/**
 * In the open context menu, opens the **Staging Group** submenu and clicks [item]. Saves a review
 * screenshot named [screenshot] while the submenu is open, when given.
 */
fun RemoteRobot.chooseFromStagingGroupMenu(item: String, screenshot: String? = null) {
    showingComponent("//div[@class='ActionMenu' and @text='Staging Group']").click()
    val menuItem = showingComponent("//div[@class='ActionMenuItem' and @text='$item']")
    if (screenshot != null) saveReviewScreenshot(screenshot)
    menuItem.click()
}

/**
 * A component matching [xpath] that is on screen. Menus hold more than one component per item, some
 * hidden, so a plain find is ambiguous.
 */
private fun RemoteRobot.showingComponent(xpath: String): ComponentFixture {
    var showing: ComponentFixture? = null
    var states = emptyList<Boolean>()
    try {
        waitFor(Duration.ofSeconds(10), Duration.ofMillis(100), "a showing component at $xpath") {
            val found = findAll(ComponentFixture::class.java, byXpath(xpath))
            states = found.map { it.callJs("component.isShowing()", true) }
            showing = found.filterIndexed { index, _ -> states[index] }.firstOrNull()
            showing != null
        }
    } catch (e: Exception) {
        throw AssertionError("no showing component at $xpath; found ${states.size}, showing: $states", e)
    }
    return showing!!
}

/** The index of the [occurrence]th row rendered exactly as [text]. */
fun JTreeFixture.rowIndex(text: String, occurrence: Int = 0): Int =
    collectRows().withIndex().filter { it.value == text }.map { it.index }.getOrNull(occurrence)
        ?: error("no row '$text' (occurrence $occurrence) in ${collectRows()}")

/** Selects the rows rendered as [texts] (each its first occurrence) with Ctrl+click, as a user would. */
fun JTreeFixture.selectRows(robot: RemoteRobot, vararg texts: String) {
    clickRow(rowIndex(texts.first()))
    Keyboard(robot).pressing(KeyEvent.VK_CONTROL) {
        texts.drop(1).forEach { clickRow(rowIndex(it)) }
    }
}
