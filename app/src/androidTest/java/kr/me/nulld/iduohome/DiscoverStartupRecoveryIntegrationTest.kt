package kr.me.nulld.iduohome

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DiscoverStartupRecoveryIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Blocks exactly the requested launches, independent of when ActivityScenario returns. */
    private class DiscoverLaunchMonitor(private val launchesToBlock: Int) : Instrumentation.ActivityMonitor() {
        private val matches = AtomicInteger()
        val blocked get() = matches.get().coerceAtMost(launchesToBlock)
        val attempts get() = matches.get()
        val recoveredAttempt get() = launchesToBlock + 1

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.component?.className != LiveDiscoverActivity::class.java.name) return null
            val match = matches.incrementAndGet()
            return if (match <= launchesToBlock)
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            else null
        }
    }

    private fun await(timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Discover timed out: message=${LiveDiscover.message.value}, page=${LiveDiscover.progress}, native=${LiveDiscover.nativePosition}" }
            SystemClock.sleep(25)
        }
    }

    private fun openDiscover() {
        val bounds = checkNotNull(UiDevice.getInstance(instrumentation)
            .wait(Until.findObject(By.desc("Discover")), 5_000)).visibleBounds
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
            .executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}")).use { it.readBytes() }
    }

    @Test fun skippedFirstLaunchRetriesWithARealHost() = verifyRecovery(blockBothAttempts = false)

    @Test fun twoSkippedLaunchesExposeRetryAndExplicitRetryRecovers() = verifyRecovery(blockBothAttempts = true)

    @Test fun disablingDiscoverClosesItsHostAndReenablingConnectsAgain() {
        assumeTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        assumeTrue(DiscoverBounds.available)
        val attachBefore = LiveDiscover.attachNativeFeed
        LiveDiscover.attachNativeFeed = true
        val monitor = DiscoverLaunchMonitor(0)
        instrumentation.addMonitor(monitor)
        var scenario: ActivityScenario<MainActivity>? = null
        var enabledBefore = true
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity {
                val model = ViewModelProvider(it)[LauncherModel::class.java]
                enabledBefore = model.state.value.discoverEnabled
                model.setDiscoverEnabled(true)
            }
            await { LiveDiscover.host.get() != null }
            val firstHost = checkNotNull(LiveDiscover.host.get())
            openDiscover()
            await(20_000) { LiveDiscover.message.value == null }
            scenario.onActivity { ViewModelProvider(it)[LauncherModel::class.java].setDiscoverEnabled(false) }
            await { LiveDiscover.host.get() == null && firstHost.isDestroyed }
            val launches = monitor.attempts
            scenario.recreate()
            scenario.onActivity {
                assertFalse(ViewModelProvider(it)[LauncherModel::class.java].state.value.discoverEnabled)
                LiveDiscover.prepare(it, LiveDiscover.viewport, LiveDiscover.pageWidth)
                LiveDiscover.retry()
                assertFalse(LiveDiscover.native(.5f))
            }
            assertNull(LiveDiscover.host.get())
            assertEquals(launches, monitor.attempts)

            scenario.onActivity { ViewModelProvider(it)[LauncherModel::class.java].setDiscoverEnabled(true) }
            await { LiveDiscover.host.get()?.let { it !== firstHost } == true }
            openDiscover()
            await(20_000) { LiveDiscover.message.value == null && LiveDiscover.nativePosition >= .99f }
        } finally {
            instrumentation.removeMonitor(monitor)
            scenario?.onActivity { ViewModelProvider(it)[LauncherModel::class.java].setDiscoverEnabled(enabledBefore) }
            scenario?.close()
            instrumentation.runOnMainSync { LiveDiscover.host.get()?.finish() }
            LiveDiscover.attachNativeFeed = attachBefore
        }
    }

    private fun verifyRecovery(blockBothAttempts: Boolean) {
        assumeTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        assumeTrue(DiscoverBounds.available)
        val attachNativeFeed = LiveDiscover.attachNativeFeed
        LiveDiscover.attachNativeFeed = true
        var oldMain = LiveDiscover.owner.get()
        var oldHost = LiveDiscover.host.get()
        instrumentation.runOnMainSync { oldMain?.finish(); oldHost?.finish() }
        await { LiveDiscover.host.get() == null }

        val monitor = DiscoverLaunchMonitor(if (blockBothAttempts) 2 else 1)
        instrumentation.addMonitor(monitor)
        var monitorInstalled = true
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            var activity: MainActivity? = null
            var model: LauncherModel? = null
            scenario.onActivity {
                activity = it
                model = ViewModelProvider(it)[LauncherModel::class.java]
            }
            await { monitor.blocked >= 1 }
            await { model?.state?.value?.loading == false }
            val main = requireNotNull(activity)
            val before = main.getSharedPreferences("launcher", 0).getString("state", null)
            val widgetIds = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
                .get(main).let { it as WidgetController }.host.appWidgetIds.toSet()

            if (blockBothAttempts) {
                await { monitor.blocked >= 2 }
                await { LiveDiscover.message.value == "Discover couldn't start. Tap Retry to reconnect." }
                instrumentation.runOnMainSync { LiveDiscover.retry() }
            }
            await { LiveDiscover.host.get()?.let { !it.isFinishing && !it.isDestroyed } == true }
            assertTrue("Recovery must launch after every deliberately blocked attempt",
                monitor.attempts >= monitor.recoveredAttempt)
            instrumentation.removeMonitor(monitor)
            monitorInstalled = false
            // A connected offscreen feed stays at position zero and is intentionally hidden.
            // Open it once so Google's real progress callback proves the recovered transport.
            openDiscover()
            await(20_000) { LiveDiscover.message.value == null }

            assertEquals(before, main.getSharedPreferences("launcher", 0).getString("state", null))
            assertEquals(widgetIds, MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
                .get(main).let { it as WidgetController }.host.appWidgetIds.toSet())
        } finally {
            try {
                if (monitorInstalled) instrumentation.removeMonitor(monitor)
                scenario?.close()
                instrumentation.runOnMainSync { LiveDiscover.host.get()?.finish() }
                await { LiveDiscover.host.get() == null }
            } finally {
                LiveDiscover.attachNativeFeed = attachNativeFeed
            }
        }
    }
}
