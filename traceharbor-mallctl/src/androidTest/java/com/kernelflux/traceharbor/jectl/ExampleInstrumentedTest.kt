package com.kernelflux.traceharbor.jectl

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        // Note: assertion string preserved verbatim from the original Java —
        // it does NOT match the module's current namespace (legacy stale stub).
        assertEquals("com.kernelflux.traceharbor_jectl.test", appContext.packageName)
    }
}
