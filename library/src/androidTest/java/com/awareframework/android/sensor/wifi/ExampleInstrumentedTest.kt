package com.awareframework.android.sensor.wifi

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.awareframework.android.sensor.wifi.model.WiFiScanData
import com.awareframework.android.core.db.Engine
import org.junit.Assert.assertEquals
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
        val appContext = InstrumentationRegistry.getTargetContext()
        assertEquals("com.aware.android.sensor.wifi.test", appContext.packageName)

        WiFiSensor.startService(appContext, WiFiSensor.WiFiConfig().apply {
            sensorObserver = object : WiFiObserver {
                override fun onWiFiAPDetected(data: WiFiScanData) {
                    // your code here...
                }

                override fun onWiFiDisabled() {
                    // your code here...
                }

                override fun onWiFiScanStarted() {
                    // your code here...
                }

                override fun onWiFiScanEnded() {
                    // your code here...
                }

            }
            dbType = Engine.DatabaseType.ROOM
        })

        Thread.sleep(10000)
    }
}
