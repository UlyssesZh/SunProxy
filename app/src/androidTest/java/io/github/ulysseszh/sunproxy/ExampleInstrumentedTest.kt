package io.github.ulysseszh.sunproxy

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
	@Test
	fun useAppContext() {
		// Context of the app under test.
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext
		Assert.assertEquals("io.github.ulysseszh.sunproxy", appContext.packageName)
	}

	@Test
	fun searchDomains() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
		Assert.assertNull(cm?.getLinkProperties(cm.activeNetwork)?.domains)
	}
}