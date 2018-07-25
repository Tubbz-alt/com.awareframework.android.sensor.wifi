package com.awareframework.android.sensor.wifi

import com.awareframework.android.sensor.wifi.model.WiFiScanData

/**
 * Observer for WiFi events.
 *
 * @author  sercant
 * @date 23/07/2018
 */
interface WiFiObserver {
    fun onWiFiAPDetected(data: WiFiScanData)
    fun onWiFiDisabled()
    fun onWiFiScanStarted()
    fun onWiFiScanEnded()
}