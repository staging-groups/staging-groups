package dev.yendrrek.staginggroups.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.EventQueue
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

class ClipboardReleaseTest {

    private val clipboard = Clipboard("test")

    @Test
    fun `text is re-published as a plain copy, dropping its owner`() {
        var lostOwnership = false
        val owner = ClipboardOwner { _, _ -> lostOwnership = true }
        val original = object : Transferable by StringSelection("Docs") {}
        clipboard.setContents(original, owner)

        ClipboardRelease.release(clipboard)
        EventQueue.invokeAndWait {} // The clipboard tells its old owner on the event queue.

        val contents = clipboard.getContents(null)
        assertTrue(contents is StringSelection)
        assertEquals("Docs", contents.getTransferData(DataFlavor.stringFlavor))
        assertTrue(lostOwnership)
    }

    @Test
    fun `contents that are more than text are re-published as they are`() {
        val image = object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
            override fun getTransferData(flavor: DataFlavor): Any = Any()
        }
        var lostOwnership = false
        clipboard.setContents(image) { _, _ -> lostOwnership = true }

        ClipboardRelease.release(clipboard)
        EventQueue.invokeAndWait {}

        assertSame(image, clipboard.getContents(null))
        assertTrue(lostOwnership)
    }

    @Test
    fun `an empty clipboard stays empty`() {
        ClipboardRelease.release(clipboard)

        assertNull(clipboard.getContents(null))
    }
}
