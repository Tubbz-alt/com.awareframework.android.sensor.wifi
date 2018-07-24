package com.aware.android.sensor.wifi.model

import com.awareframework.android.core.model.AwareObject

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
}