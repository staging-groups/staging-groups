package dev.yendrrek.staginggroups.ui

import com.intellij.openapi.extensions.LoadingOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Where the platform puts the plugin's colour provider among the others, as it sorts them on load:
 * PrismTabs first when installed, then Staging Groups, then the IDE's own File Colors provider. The
 * platform asks them in that order and takes the first colour, so PrismTabs keeps a tab both colour.
 */
class ColourProviderOrderTest {

    private class Provider(override val orderId: String?, override val order: LoadingOrder) : LoadingOrder.Orderable {
        override fun toString(): String = orderId.orEmpty()
    }

    /** As PrismTabs registers its provider. */
    private val prismTabs = Provider("prismtabs", LoadingOrder.FIRST)

    /** As the IDE registers its File Colors provider: no id, no order. */
    private val fileColours = Provider("fileColours", LoadingOrder.ANY)

    private val stagingGroups = registeredProvider()

    @Test
    fun `PrismTabs comes first, then Staging Groups, then File Colors`() {
        assertEquals(listOf(prismTabs, stagingGroups, fileColours), sorted(fileColours, stagingGroups, prismTabs))
        assertEquals(listOf(prismTabs, stagingGroups, fileColours), sorted(stagingGroups, prismTabs, fileColours))
    }

    @Test
    fun `without PrismTabs, Staging Groups still comes before File Colors`() {
        assertEquals(listOf(stagingGroups, fileColours), sorted(fileColours, stagingGroups))
    }

    private fun sorted(vararg providers: Provider): List<Provider> =
        providers.toMutableList().also { LoadingOrder.sortByLoadingOrder(it) }

    /** The plugin's own registration, read from its plugin.xml. */
    private fun registeredProvider(): Provider {
        val pluginXml = File(System.getProperty("user.dir"), "src/main/resources/META-INF/plugin.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pluginXml)
        val element = document.getElementsByTagName("editorTabColorProvider").item(0) as Element
        return Provider(element.getAttribute("id"), LoadingOrder.readOrder(element.getAttribute("order")))
    }
}
