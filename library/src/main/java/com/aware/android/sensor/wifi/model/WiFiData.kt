package com.aware.android.sensor.wifi.model

import com.awareframework.android.core.model.AwareObject

/**
 * Contains the mobile device’s Wi-Fi sensor information.
 *
 * @author  sercant
 * @date 23/07/2018
 */
class WiFiData(
        var macAddress: String? = null,
        var bssid: String? = null,
        var ssid: String? = null,
        var security: String? = null,
        var frequency: Int? = null,
        var rssi: Int? = null
) : AwareObject(jsonVersion = 1) {
    companion object {
        const val TABLE_NAME = "wifiData"
    }
}