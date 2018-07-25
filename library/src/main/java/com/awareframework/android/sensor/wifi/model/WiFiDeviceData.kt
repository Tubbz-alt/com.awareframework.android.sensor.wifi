package com.awareframework.android.sensor.wifi.model

import com.awareframework.android.core.model.AwareObject
import com.google.gson.Gson

/**
 * Contains the mobile device’s Wi-Fi sensor information.
 *
 * @author  sercant
 * @date 24/07/2018
 */
class WiFiDeviceData(
        var macAddress: String? = null,
        var bssid: String? = null,
        var ssid: String? = null
) : AwareObject(jsonVersion = 1) {
    companion object {
        const val TABLE_NAME = "wifiDeviceData"
    }

    override fun toString(): String = Gson().toJson(this)
}