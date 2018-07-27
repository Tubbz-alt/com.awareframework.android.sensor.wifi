package com.awareframework.android.sensor.wifi

import android.Manifest
import android.app.AlarmManager
import android.app.AlarmManager.RTC_WAKEUP
import android.app.IntentService
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
import android.os.IBinder
import android.support.v4.content.ContextCompat
import android.util.Log
import com.awareframework.android.sensor.wifi.model.WiFiScanData
import com.awareframework.android.sensor.wifi.model.WiFiDeviceData
import com.awareframework.android.core.AwareSensor
import com.awareframework.android.core.db.Engine
import com.awareframework.android.core.model.SensorConfig
import com.google.gson.Gson
import java.util.concurrent.Executors
import kotlin.concurrent.thread


/**
 * WiFi Module. Scans and returns surrounding WiFi AccessPoints devices information and RSSI dB values.
 *
 * @author  sercant
 * @date 23/07/2018
 */
class WiFiSensor : AwareSensor(), WiFiObserver {

    companion object {

        const val TAG = "AwareWiFiSensor"

        const val ACTION_AWARE_WIFI_START = "com.aware.android.sensor.wifi.SENSOR_START"
        const val ACTION_AWARE_WIFI_STOP = "com.aware.android.sensor.wifi.SENSOR_STOP"
        const val ACTION_AWARE_WIFI_SYNC = "com.aware.android.sensor.wifi.SYNC"
        const val ACTION_AWARE_WIFI_SET_LABEL = "com.aware.android.sensor.wifi.SET_LABEL"
        const val EXTRA_LABEL = "label"

        /**
         * Broadcasted event: currently connected to this AP
         */
        const val ACTION_AWARE_WIFI_CURRENT_AP = "ACTION_AWARE_WIFI_CURRENT_AP"

        /**
         * Broadcasted event: new WiFi AP device detected
         */
        const val ACTION_AWARE_WIFI_NEW_DEVICE = "ACTION_AWARE_WIFI_NEW_DEVICE"
        const val EXTRA_DATA = "data"

        /**
         * Broadcasted event: WiFi scan started
         */
        const val ACTION_AWARE_WIFI_SCAN_STARTED = "ACTION_AWARE_WIFI_SCAN_STARTED"

        /**
         * Broadcasted event: WiFi scan ended
         */
        const val ACTION_AWARE_WIFI_SCAN_ENDED = "ACTION_AWARE_WIFI_SCAN_ENDED"

        /**
         * Broadcast receiving event: request a WiFi scan
         */
        const val ACTION_AWARE_WIFI_REQUEST_SCAN = "ACTION_AWARE_WIFI_REQUEST_SCAN"

        fun startService(context: Context, config: WiFiConfig? = null) {
            if (config != null)
                CONFIG.replaceWith(config)
            context.startService(Intent(context, WiFiSensor::class.java))
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, WiFiSensor::class.java))
        }

        val CONFIG: WiFiConfig = WiFiConfig()

        var instance: WiFiSensor? = null

        val REQUIRED_PERMISSIONS = arrayOf(
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_NETWORK_STATE
        )
    }

    private lateinit var alarmManager: AlarmManager
    private var wifiManager: WifiManager? = null
    private lateinit var wifiScan: PendingIntent
    private lateinit var backgroundService: Intent

    private val wifiMonitor = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                SCAN_RESULTS_AVAILABLE_ACTION -> {
                    context?.startService(Intent(context, BackgroundService::class.java).apply {
                        action = SCAN_RESULTS_AVAILABLE_ACTION
                    })
                }
            }
        }
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                ACTION_AWARE_WIFI_SET_LABEL -> {
                    intent.getStringExtra(EXTRA_LABEL)?.let {
                        CONFIG.label = it
                    }
                }

                ACTION_AWARE_WIFI_SYNC -> onSync(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        dbEngine = Engine.Builder(this)
                .setType(CONFIG.dbType)
                .setPath(CONFIG.dbPath)
                .setHost(CONFIG.dbHost)
                .setEncryptionKey(CONFIG.dbEncryptionKey)
                .build()

        alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        registerReceiver(wifiMonitor, IntentFilter().apply {
            addAction(SCAN_RESULTS_AVAILABLE_ACTION)
        })

        registerReceiver(wifiReceiver, IntentFilter().apply {
            addAction(ACTION_AWARE_WIFI_SET_LABEL)
            addAction(ACTION_AWARE_WIFI_SYNC)
        })

        backgroundService = Intent(this, BackgroundService::class.java).apply {
            action = ACTION_AWARE_WIFI_REQUEST_SCAN
        }
        wifiScan = PendingIntent.getService(this, 0, backgroundService, PendingIntent.FLAG_UPDATE_CURRENT)

        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (REQUIRED_PERMISSIONS.any { ContextCompat.checkSelfPermission(this, it) != PERMISSION_GRANTED }) {
            logw("Missing permissions detected.")
            return START_NOT_STICKY
        }

        // TODO Check permissions
        if (wifiManager == null) {
            logw("This device does not have a WiFi chip")
            stopSelf()
        } else {
            alarmManager.cancel(wifiScan)
            alarmManager.setRepeating(
                    RTC_WAKEUP,
                    System.currentTimeMillis() + 1000,
                    (CONFIG.frequency * 60000).toLong(),
                    wifiScan)

            logd("WiFi service active.")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(wifiMonitor)
        unregisterReceiver(wifiReceiver)
        alarmManager.cancel(wifiScan)

        logd("WiFi service terminated.")

        instance = null
    }


    override fun onWiFiAPDetected(data: WiFiScanData) {

    }

    override fun onWiFiDisabled() {
        dbEngine?.save(WiFiScanData().apply {
            deviceId = CONFIG.deviceId
            timestamp = System.currentTimeMillis()
            label = "disabled"
        }, WiFiScanData.TABLE_NAME)

        CONFIG.sensorObserver?.onWiFiDisabled()
    }

    fun onWiFiResultsAvailable() {
        val wifi = wifiManager?.connectionInfo ?: return

        // synchronously get the AP we are currently connected to.
        val wifiInfo = thread(start = false) {
            val data = WiFiDeviceData().apply {
                deviceId = CONFIG.deviceId
                timestamp = System.currentTimeMillis()
                macAddress = wifi.macAddress    // TODO add optional encryption
                bssid = wifi.bssid              // TODO add optional encryption
                ssid = wifi.ssid                // TODO add optional encryption
            }
            dbEngine?.save(data, WiFiScanData.TABLE_NAME)

            sendBroadcast(Intent(ACTION_AWARE_WIFI_CURRENT_AP).apply {
                putExtra(EXTRA_DATA, Gson().toJson(data)) // TODO fix
            })

            logd("WiFi local sensor information: $data")
        }

        // Asynchronously get the AP we are currently connected to.
        val scanResults = thread(start = false) {
            val aps = wifiManager?.scanResults ?: return@thread
            logd("Found ${aps.size} access points.")

            val currentScan = System.currentTimeMillis()

            for (ap in aps) {
                ap ?: continue

                val data = WiFiScanData().apply {
                    deviceId = CONFIG.deviceId
                    timestamp = currentScan
                    bssid = ap.BSSID
                    ssid = ap.SSID
                    security = ap.capabilities
                    frequency = ap.frequency
                    rssi = ap.level
                }

                logd("$ACTION_AWARE_WIFI_NEW_DEVICE: $data")

                CONFIG.sensorObserver?.onWiFiAPDetected(data)
                sendBroadcast(Intent(ACTION_AWARE_WIFI_NEW_DEVICE).apply {
                    putExtra(EXTRA_DATA, Gson().toJson(data))
                })
            }

            logd(ACTION_AWARE_WIFI_SCAN_ENDED)
            sendBroadcast(Intent(ACTION_AWARE_WIFI_SCAN_ENDED))
        }

        val executor = Executors.newSingleThreadExecutor()
        executor.submit(wifiInfo)
        executor.submit(scanResults)
        executor.shutdown()

        instance?.onWiFiScanEnded()
    }

    override fun onWiFiScanStarted() {
        CONFIG.sensorObserver?.onWiFiScanStarted()
    }

    override fun onWiFiScanEnded() {
        CONFIG.sensorObserver?.onWiFiScanEnded()
    }

    override fun onSync(intent: Intent?) {
        dbEngine?.startSync(WiFiScanData.TABLE_NAME)
        dbEngine?.startSync(WiFiDeviceData.TABLE_NAME)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    data class WiFiConfig(
            var sensorObserver: WiFiObserver? = null,
            var frequency: Float = 1f
    ) : SensorConfig(dbPath = "aware_wifi") {
        override fun <T : SensorConfig> replaceWith(config: T) {
            super.replaceWith(config)

            if (config is WiFiConfig) {
                sensorObserver = config.sensorObserver
                frequency = config.frequency
            }
        }
    }

    /**
     * Background service for WiFi module
     * - ACTION_AWARE_WIFI_REQUEST_SCAN
     * - {@link WifiManager#SCAN_RESULTS_AVAILABLE_ACTION}
     * - ACTION_AWARE_WEBSERVICE
     */
    class BackgroundService : IntentService("$TAG background service") {
        override fun onHandleIntent(intent: Intent?) {
            intent ?: return

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            when (intent.action) {
                ACTION_AWARE_WIFI_REQUEST_SCAN -> {
                    try {
                        if (wifiManager.isWifiEnabled) {
                            logd(ACTION_AWARE_WIFI_SCAN_STARTED)

                            sendBroadcast(Intent(ACTION_AWARE_WIFI_SCAN_STARTED))

                            wifiManager.startScan()

                            instance?.onWiFiScanStarted()
                        } else {
                            throw NullPointerException()
                        }
                    } catch (e: NullPointerException) {
                        logd("WiFi is off.")

                        instance?.onWiFiDisabled()
                    }
                }
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    instance?.onWiFiResultsAvailable()
                }
            }
        }
    }

    class WiFiSensorBroadcastReceiver : AwareSensor.SensorBroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return

            logd("Sensor broadcast received. action: " + intent?.action)

            when (intent?.action) {
                AwareSensor.SensorBroadcastReceiver.SENSOR_START_ENABLED -> {
                    logd("Sensor enabled: " + CONFIG.enabled)

                    if (CONFIG.enabled) {
                        startService(context)
                    }
                }

                ACTION_AWARE_WIFI_STOP,
                AwareSensor.SensorBroadcastReceiver.SENSOR_STOP_ALL -> {
                    logd("Stopping sensor.")
                    stopService(context)
                }

                ACTION_AWARE_WIFI_START -> {
                    startService(context)
                }
            }
        }
    }
}

private fun logd(text: String) {
    if (WiFiSensor.CONFIG.debug) Log.d(WiFiSensor.TAG, text)
}

private fun logw(text: String) {
    Log.w(WiFiSensor.TAG, text)
}