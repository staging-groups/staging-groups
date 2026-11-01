package dev.yendrrek.staginggroups.lifecycle

import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

/**
 * Re-publishes a clipboard's contents so that nothing on it reaches the plugin's classes, which would
 * keep its class loader alive after an unload.
 *
 * Selecting text in a Swing text field makes its caret the owner of the clipboard (on Linux, the
 * primary selection), and copying from one puts a transferable holding the field there. Either way
 * the clipboard reaches the field's dialog, and from the **New Staging Group** dialog on to the
 * plugin's name validator:
 *
 * ```
 * XClipboard -> owner: DefaultCaret$Handler -> JTextField -> Messages$InputDialog -> NameValidator
 * ```
 *
 * The replacement must be a JDK class: any object of the plugin's left on the clipboard pins the
 * loader just as firmly.
 */
internal object ClipboardRelease {

    fun release(clipboard: Clipboard) {
        val contents = clipboard.getContents(null) ?: return
        // Only this JVM's own contents come back as the same instance twice; another application's
        // are rebuilt on every call, and are left alone.
        if (contents !== clipboard.getContents(null)) return
        clipboard.setContents(contents.plainTextCopy() ?: contents, null)
    }

    /**
     * The contents as a [StringSelection], or `null` when they are more than text, such as an image
     * or a file list, and a copy would lose something. Re-publishing those as they are still drops
     * the owner.
     */
    private fun Transferable.plainTextCopy(): StringSelection? {
        if (!isDataFlavorSupported(DataFlavor.stringFlavor)) return null
        if (!transferDataFlavors.all { it.isFlavorTextType || it.representationClass == String::class.java }) return null
        val text = try {
            getTransferData(DataFlavor.stringFlavor)
        } catch (e: Exception) {
            return null
        }
        return (text as? String)?.let(::StringSelection)
    }
}
