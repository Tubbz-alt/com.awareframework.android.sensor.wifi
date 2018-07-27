# AWARE WiFi

[![jitpack-badge](https://jitpack.io/v/awareframework/com.aware.android.sensor.wifi.svg)](https://jitpack.io/#awareframework/com.aware.android.sensor.wifi)

Logs the mobile device’s Wi-Fi sensor, current AP and surrounding Wi-Fi visible devices with respective RSSI dB values at specified intervals.

> Android can take up to 60 seconds to resolve all the found Wi-Fi device’s names. There is no way around this. The default and recommended scanning interval is 1 minute or higher.

## Public functions

### WiFiSensor

+ `startService(context: Context, config: WiFiConfig?)`: Starts the Wi-Fi sensor with the optional configuration.
+ `stopService(context: Context)`: Stops the Wi-Fi service.
+ `REQUIRED_PERMISSIONS`: An array of required android permissions for this module.

### WiFiConfig

Class to hold the configuration of the Wi-Fi sensor.

#### Fields

+ `debug: Boolean`: enable/disable logging to `Logcat`. (default = false)
+ `host: String`: Host for syncing the database. (default = null)
+ `key: String`: Encryption key for the database. (default = no encryption)
+ `host: String`: Host for syncing the database. (default = null)
+ `type: EngineDatabaseType)`: Which db engine to use for saving data. (default = NONE)
+ `path: String`: Path of the database.
+ `deviceId: String`: Id of the device that will be associated with the events and the sensor. (default = "")
+ `sensorObserver: WiFiObserver`: Callback for live data updates.
+ `frequency: Float`: Frequency of the Wi-Fi data querying in minutes. (default = 1f)

## Broadcasts

+ `WiFiSensor.ACTION_AWARE_WIFI_CURRENT_AP` currently connected to this AP. In the extras, `WiFiSensor.EXTRA_DATA` includes the WiFiData in json string format.
+ `WiFiSensor.ACTION_AWARE_WIFI_NEW_DEVICE` new WiFi AP device detected. In the extras, `WiFiSensor.EXTRA_DATA` includes the WiFiData in json string format.
+ `WiFiSensor.ACTION_AWARE_WIFI_SCAN_STARTED` WiFi scan started
+ `WiFiSensor.ACTION_AWARE_WIFI_SCAN_ENDED` WiFi scan ended.

## Data Representations

### WiFi Device Data

| Field      | Type   | Description                                   |
| ---------- | ------ | --------------------------------------------- |
| macAddress | String | device’s MAC address                         |
| bssid      | String | currently connected access point MAC address  |
| ssid       | String | currently connected access point network name |
| deviceId   | String | AWARE device UUID                             |
| timestamp  | Long   | unixtime milliseconds since 1970              |
| timezone   | Int    | WiFi of the device                            |
| os         | String | Operating system of the device (ex. android)  |

### WiFi Scan Data

| Field     | Type   | Description                                    |
| --------- | ------ | ---------------------------------------------- |
| bssid     | String | currently connected access point MAC address   |
| ssid      | String | currently connected access point network name  |
| security  | String | active security protocols                      |
| frequency | Int    | Wi-Fi band frequency (e.g., 2427, 5180), in Hz |
| rssi      | Int    | RSSI dB to the scanned device                  |
| deviceId  | String | AWARE device UUID                              |
| timestamp | Long   | unixtime milliseconds since 1970               |
| timezone  | Int    | WiFi of the device                             |
| os        | String | Operating system of the device (ex. android)   |

## Example usage

```kotlin
// To start the service.
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
    // more configuration...
})

// To stop the service
WiFiSensor.stopService(appContext)
```

## License

Copyright (c) 2018 AWARE Mobile Context Instrumentation Middleware/Framework (http://www.awareframework.com)

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
