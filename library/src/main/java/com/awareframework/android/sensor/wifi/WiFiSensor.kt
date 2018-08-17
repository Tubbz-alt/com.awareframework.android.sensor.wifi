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
import com.awareframework.android.core.AwareSensor
import com.awareframework.android.core.model.SensorConfig
import com.awareframework.android.sensor.wifi.model.WiFiDeviceData
import com.awareframework.android.sensor.wifi.model.WiFiScanData
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

        const val TAG = "Aware::WiFi"

        /**
         * Received event: Fire it to start the WiFi sensor.
         */
        const val ACTION_AWARE_WIFI_START = "com.aware.android.sensor.wifi.SENSOR_START"

        /**
         * Received event: Fire it to stop the WiFi sensor.
         */
        const val ACTION_AWARE_WIFI_STOP = "com.aware.android.sensor.wifi.SENSOR_STOP"

        /**
         * Received event: Fire it to sync the data with the server.
         */
        const val ACTION_AWARE_WIFI_SYNC = "com.aware.android.sensor.wifi.SYNC"

        /**
         * Received event: Fire it to set the data label.
         * Use [EXTRA_LABEL] to send the label string.
         */
        const val ACTION_AWARE_WIFI_SET_LABEL = "com.aware.android.sensor.wifi.SET_LABEL"

        /**
         * Label string sent in the intent extra.
         */
        const val EXTRA_LABEL = "label"

        /**
         * Fired event: currently connected to this AP
         */
        const val ACTION_AWARE_WIFI_CURRENT_AP = "ACTION_AWARE_WIFI_CURRENT_AP"

        /**
         * Fired event: new WiFi AP device detected.
         * [WiFiSensor.EXTRA_DATA] contains the JSON version of the discovered device.
         */
        const val ACTION_AWARE_WIFI_NEW_DEVICE = "ACTION_AWARE_WIFI_NEW_DEVICE"

        /**
         * Contains the JSON version of the discovered device.
         */
        const val EXTRA_DATA = "data"

        /**
         * Fired event: WiFi scan started.
         */
        const val ACTION_AWARE_WIFI_SCAN_STARTED = "ACTION_AWARE_WIFI_SCAN_STARTED"

        /**
         * Fired event: WiFi scan ended.
         */
        const val ACTION_AWARE_WIFI_SCAN_ENDED = "ACTION_AWARE_WIFI_SCAN_ENDED"

        /**
         * Broadcast receiving event: request a WiFi scan
         */
        const val ACTION_AWARE_WIFI_REQUEST_SCAN = "ACTION_AWARE_WIFI_REQUEST_SCAN"

        /**
         * Start the sensor with the given optional configuration.
         */
        fun start(context: Context, config: Config? = null) {
            if (config != null)
                CONFIG.replaceWith(config)
            context.startService(Intent(context, WiFiSensor::class.java))
        }

        /**
         * Stop the service if it's currently running.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, WiFiSensor::class.java))
        }

        /**
         * Current configuration of the WiFiSensor. Some changes in the configuration will have
         * immediate effect.
         */
        val CONFIG: Config = Config()


        private var instance: WiFiSensor? = null

        /**
         * Required permissions to ask in the runtime to use this sensor.
         */
        val REQUIRED_PERMISSIONS = arrayOf(
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_NETWORK_STATE
        )
    }

    /**
     * Alarm manager to initiate wi-fi scans.
     */
    private lateinit var alarmManager: AlarmManager

    private var wifiManager: WifiManager? = null

    private lateinit var wifiScan: PendingIntent
    private lateinit var backgroundService: Intent

    /**
     * Listens [WifiManager.SCAN_RESULTS_AVAILABLE_ACTION] to start the background service.
     */
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

    /**
     * Listens [ACTION_AWARE_WIFI_SET_LABEL] and [ACTION_AWARE_WIFI_SYNC].
     */
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

        initializeDbEngine(CONFIG)

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

        dbEngine?.close()
    }


    override fun onWiFiAPDetected(data: WiFiScanData) {

    }

    /**
     * Save the event to the db and trigger the function on the observer.
     */
    override fun onWiFiDisabled() {
        dbEngine?.save(WiFiScanData().apply {
            deviceId = CONFIG.deviceId
            timestamp = System.currentTimeMillis()
            label = "disabled"
        }, WiFiScanData.TABLE_NAME)

        CONFIG.sensorObserver?.onWiFiDisabled()
    }

    /**
     * Save the results of the wi-fi scan.
     */
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

    /**
     * Trigger the sensor observer when wi-fi scan has started.
     */
    override fun onWiFiScanStarted() {
        CONFIG.sensorObserver?.onWiFiScanStarted()
    }

    /**
     * Trigger the sensor observer when wi-fi scan has ended.
     */
    override fun onWiFiScanEnded() {
        CONFIG.sensorObserver?.onWiFiScanEnded()
    }

    /**
     * Sync the related fields of the db to server.
     */
    override fun onSync(intent: Intent?) {
        dbEngine?.startSync(WiFiScanData.TABLE_NAME)
        dbEngine?.startSync(WiFiDeviceData.TABLE_NAME)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Configuration of the sensor.
     */
    data class Config(
            var sensorObserver: Observer? = null,
            var frequency: Float = 1f
    ) : SensorConfig(dbPath = "aware_wifi") {
        override fun <T : SensorConfig> replaceWith(config: T) {
            super.replaceWith(config)

            if (config is Config) {
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

    /**
     * Listens to [AwareSensor.SensorBroadcastReceiver.SENSOR_START_ENABLED], [ACTION_AWARE_WIFI_STOP],
     * [AwareSensor.SensorBroadcastReceiver.SENSOR_STOP_ALL], [ACTION_AWARE_WIFI_START] events.
     */
    class WiFiSensorBroadcastReceiver : AwareSensor.SensorBroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return

            logd("Sensor broadcast received. action: " + intent?.action)

            when (intent?.action) {
                SENSOR_START_ENABLED -> {
                    logd("Sensor enabled: " + CONFIG.enabled)

                    if (CONFIG.enabled) {
                        start(context)
                    }
                }

                ACTION_AWARE_WIFI_STOP,
                SENSOR_STOP_ALL -> {
                    logd("Stopping sensor.")
                    stop(context)
                }

                ACTION_AWARE_WIFI_START -> {
                    start(context)
                }
            }
        }
    }

    /**
     * Observer to listen to live data updates.
     */
    interface Observer {
        /**
         * Called when a wi-fi AP detected.
         *
         * @param data: Detected wi-fi AP.
         */
        fun onWiFiAPDetected(data: WiFiScanData)

        /**
         * Called when the wi-fi disabled.
         */
        fun onWiFiDisabled()

        /**
         * Called when the scan has started.
         */
        fun onWiFiScanStarted()

        /**
         * Called when the wi-fi scan finished.
         */
        fun onWiFiScanEnded()
    }
}

private fun logd(text: String) {
    if (WiFiSensor.CONFIG.debug) Log.d(WiFiSensor.TAG, text)
}

private fun logw(text: String) {
    Log.w(WiFiSensor.TAG, text)
}