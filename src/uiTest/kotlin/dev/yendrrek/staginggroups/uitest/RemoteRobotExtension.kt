package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.Keyboard
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.opentest4j.TestAbortedException
import java.io.File

/**
 * Resolves a [RemoteRobot] parameter pointed at `runIdeForUiTests`.
 *
 * Before each test it closes leftover popups and raises the IDE frame. After it, a failed test saves
 * a screenshot to `build/reports/ui-test`, and a passed one still fails if the plugin logged an error
 * to `idea.log` while it ran.
 */
class RemoteRobotExtension : BeforeTestExecutionCallback, AfterTestExecutionCallback, ParameterResolver {

    private val remoteRobot = RemoteRobot(REMOTE_ROBOT_URL)

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext) =
        parameterContext.parameter.type == RemoteRobot::class.java

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        remoteRobot

    override fun beforeTestExecution(context: ExtensionContext) {
        context.getStore(NAMESPACE).put(LOG_OFFSET, remoteRobot.ideaLogLength())
        repeat(3) { Keyboard(remoteRobot).escape() }
        remoteRobot.runJs(
            """
            importClass(java.awt.Frame)
            var frames = Frame.getFrames()
            for (var i = 0; i < frames.length; i++) {
                // `var`, not `const`: robot-server's Rhino binds a loop-body `const` only once.
                var f = frames[i]
                if (f.getClass().getName().indexOf('IdeFrameImpl') === -1) continue
                f.setExtendedState(Frame.NORMAL)
                f.toFront()
            }
            """,
            true,
        )
    }

    override fun afterTestExecution(context: ExtensionContext) {
        val outcome = context.executionException.orElse(null)
        if (outcome == null) {
            failOnPluginErrors(context)
            return
        }
        // An `assumeTrue` skip arrives here as TestAbortedException; it is not a failure.
        if (outcome is TestAbortedException) return
        // Painted offscreen rather than grabbed with java.awt.Robot: a screen capture can block
        // robot-server's single request thread forever (Wayland portal), wedging every later test.
        val bytes = remoteRobot.callJs<ByteArray>(
            """
            importPackage(java.awt)
            importPackage(java.awt.image)
            importPackage(java.io)
            importPackage(javax.imageio)

            var screen = Toolkit.getDefaultToolkit().getScreenSize()
            var image = new BufferedImage(screen.width, screen.height, BufferedImage.TYPE_INT_RGB)
            var canvas = image.createGraphics()
            canvas.setColor(Color.DARK_GRAY)
            canvas.fillRect(0, 0, screen.width, screen.height)
            var windows = Window.getWindows()
            for (var w = 0; w < windows.length; w++) {
                var target = windows[w]
                if (!target.isShowing() || target.getWidth() <= 0 || target.getHeight() <= 0) continue
                try {
                    var at = target.getLocationOnScreen()
                    var pen = canvas.create(at.x, at.y, target.getWidth(), target.getHeight())
                    try { target.paint(pen) } finally { pen.dispose() }
                } catch (ignored) { }
            }
            canvas.dispose()
            var baos = new ByteArrayOutputStream()
            ImageIO.write(image, "png", baos)
            baos.toByteArray()
            """,
            true,
        )
        File("build/reports/ui-test").apply { mkdirs() }
            .resolve("${context.requiredTestMethod.name}.png")
            .writeBytes(bytes)
    }

    private fun failOnPluginErrors(context: ExtensionContext) {
        val offset = context.getStore(NAMESPACE).get(LOG_OFFSET, Long::class.javaObjectType) ?: return
        val errors = pluginErrors(remoteRobot.ideaLogFrom(offset))
        if (errors.isNotEmpty()) {
            throw AssertionError("Staging Groups logged ${errors.size} error(s) during this test:\n\n" + errors.joinToString("\n\n"))
        }
    }

    private companion object {
        val NAMESPACE: ExtensionContext.Namespace = ExtensionContext.Namespace.create(RemoteRobotExtension::class.java)
        const val LOG_OFFSET = "ideaLogOffset"
        val REMOTE_ROBOT_URL: String = System.getProperty("remote-robot-url") ?: "http://127.0.0.1:8082"
    }
}
