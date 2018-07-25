package com.awareframework.android.sensor.wifi.model

import com.awareframework.android.core.model.AwareObject
import com.google.gson.Gson

/**
 * Contains the scan results data.
 *
 * @author  sercant
 * @date 23/07/2018
 */
class WiFiScanData(
        var bssid: String? = null,
        var ssid: String? = null,
        var security: String? = null,
        var frequency: Int? = null,
        var rssi: Int? = null
) : AwareObject(jsonVersion = 1) {
    companion object {
        const val TABLE_NAME = "wifiScanData"
    }

    override fun toString(): String = Gson().toJson(this)
}