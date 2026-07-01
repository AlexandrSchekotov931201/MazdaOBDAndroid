package car.mazda.obd.android.feature.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import car.mazda.obd.android.R
import car.mazda.obd.android.core.elm.OBDClient
import car.mazda.obd.android.core.elm.OBDSessionManager
import car.mazda.obd.android.core.elm.OBDSessionState
import car.mazda.obd.android.core.elm.transport.WifiElmTransport
import car.mazda.obd.android.core.logs.AppLogger
import car.mazda.obd.android.core.sound.SoundPatterns
import car.mazda.obd.android.core.sound.SoundPlayer
import car.mazda.obd.android.core.telemetry.StandardPidCatalog
import car.mazda.obd.android.core.telemetry.TelemetryMetric
import car.mazda.obd.android.core.telemetry.TelemetryPollingEngine
import car.mazda.obd.android.feature.location.AndroidLocationDataSource
import car.mazda.obd.android.feature.settings.AdapterConnectionPreferences
import car.mazda.obd.android.feature.trip.EngineRpmSample
import car.mazda.obd.android.feature.trip.TripState
import car.mazda.obd.android.feature.trip.TripStateManager
import car.mazda.obd.android.feature.trip.summary.TripSummaryRepository
import car.mazda.obd.android.feature.trip.summary.TripSummaryTracker
import car.mazda.obd.android.feature.trip.route.RouteTelemetry
import car.mazda.obd.android.feature.trip.route.TripRoutePreferences
import car.mazda.obd.android.feature.trip.route.TripRouteRecorder
import car.mazda.obd.android.feature.trip.route.TripRouteRepository
import car.mazda.obd.android.feature.warmup.EngineTemperatureSample
import car.mazda.obd.android.feature.warmup.EngineWarmupGuidance
import car.mazda.obd.android.feature.warmup.WarmupWarning
import car.mazda.obd.android.feature.warmup.WarmupWarningManager
import car.mazda.obd.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ObdMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pollingTargets = StandardPidCatalog.Default
    private val responseMapper = TelemetryResponseMapper()
    private val tripStateManager = TripStateManager()
    private val warmupWarningManager = WarmupWarningManager()
    private val tripSummaryTracker = TripSummaryTracker()
    private val soundPlayer = SoundPlayer()

    private lateinit var client: OBDClient
    private lateinit var sessionManager: OBDSessionManager
    private lateinit var pollingEngine: TelemetryPollingEngine
    private lateinit var tripSummaryRepository: TripSummaryRepository
    private lateinit var notificationManager: NotificationManager
    private lateinit var overlayController: ObdOverlayController
    private lateinit var preferences: ObdMonitorPreferences
    private lateinit var routePreferences: TripRoutePreferences
    private lateinit var routeRecorder: TripRouteRecorder

    private var latestRpm = 0
    private var latestValidRpm = 0
    private var latestValidRpmAtMs = 0L
    private var latestCoolantTemp: Int? = null
    private var monitorJob: Job? = null
    private var notificationJob: Job? = null
    private var endpointReconnectJob: Job? = null
    private var nextConnectionIsReconnect = false

    override fun onCreate() {
        super.onCreate()
        createObdConnectionStack()
        tripSummaryRepository = TripSummaryRepository(applicationContext)
        notificationManager = getSystemService(NotificationManager::class.java)
        overlayController = ObdOverlayController(applicationContext)
        preferences = ObdMonitorPreferences(applicationContext)
        routePreferences = TripRoutePreferences(applicationContext)
        routeRecorder = TripRouteRecorder(
            scope = scope,
            locationDataSource = AndroidLocationDataSource(applicationContext),
            repository = TripRouteRepository(applicationContext),
            telemetry = { RouteTelemetry(latestRpm, latestCoolantTemp) },
            onPointSaved = {
                ObdMonitorStateStore.update { it.copy(tripRouteVersion = it.tripRouteVersion + 1) }
            },
        )
        createNotificationChannel()
        ObdMonitorStateStore.update {
            it.copy(
                floatingWidgetEnabled = preferences.floatingWidgetEnabled,
                floatingWidgetSize = preferences.floatingWidgetSize,
                continueAfterAppClosed = preferences.continueAfterAppClosed,
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_ROUTE_RECORDING -> refreshRouteRecording()
            ACTION_VALIDATE_SAVED_ENDPOINT -> validateSavedEndpoint()
            ACTION_START_TRIP -> startTrip()
            ACTION_STOP_TRIP -> stopTrip()
            else -> {
                if (AdapterConnectionPreferences(this).loadVerified() != null) {
                    nextConnectionIsReconnect = true
                    startMonitoring()
                } else {
                    ObdMonitorStateStore.update {
                        it.copy(
                            connectionStatus = MonitorConnectionStatus.Offline,
                            connectionError = "Adapter address has not been verified",
                        )
                    }
                }
            }
        }
        return if (preferences.continueAfterAppClosed) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!preferences.continueAfterAppClosed) {
            AppLogger.log("App task removed; stopping OBD monitoring")
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        notificationJob?.cancel()
        endpointReconnectJob?.cancel()
        routeRecorder.stop()
        runBlocking(Dispatchers.IO + NonCancellable) {
            sessionManager.stopSession()
        }
        overlayController.hide()
        soundPlayer.release()
        scope.cancel()
        ObdMonitorStateStore.stop()
        ObdStatusWidgetProvider.updateAll(applicationContext)
        super.onDestroy()
    }

    private fun startMonitoring(connectSession: Boolean = true) {
        if (monitorJob?.isActive == true) return

        ObdMonitorStateStore.update {
            it.copy(
                isRunning = true,
                connectionStatus = if (!connectSession) MonitorConnectionStatus.Ready
                    else if (nextConnectionIsReconnect) MonitorConnectionStatus.Reconnecting
                    else MonitorConnectionStatus.Connecting,
                connectionError = null,
            )
        }
        startAsForeground(includeLocation = false)

        monitorJob = scope.launch {
            launch { observeSessionState() }
            launch { observeTelemetryState() }
            launch { observeTripState() }
            if (connectSession) launch { connectInitialSession() }
        }

        if (notificationJob?.isActive != true) {
            notificationJob = scope.launch {
                ObdMonitorStateStore.state.collect { state ->
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                    overlayController.update(state)
                    ObdStatusWidgetProvider.updateAll(applicationContext)
                }
            }
        }
    }

    private fun validateSavedEndpoint() {
        if (endpointReconnectJob?.isActive == true) return
        startAsForeground(includeLocation = false)
        endpointReconnectJob = scope.launch {
            val endpoint = requireNotNull(AdapterConnectionPreferences(this@ObdMonitorService).load())
            AppLogger.log("Validating updated OBD adapter endpoint")
            ObdMonitorStateStore.update {
                it.copy(
                    connectionStatus = MonitorConnectionStatus.Connecting,
                    connectionError = null,
                )
            }
            monitorJob?.cancelAndJoin()
            sessionManager.stopSession()
            createObdConnectionStack(endpoint)
            runCatching { sessionManager.startSession() }
                .onSuccess {
                    AdapterConnectionPreferences(this@ObdMonitorService).markVerified(endpoint)
                    startMonitoring(connectSession = false)
                }
                .onFailure { error ->
                    sessionManager.stopSession()
                    ObdMonitorStateStore.update {
                        it.copy(
                            isRunning = false,
                            connectionStatus = MonitorConnectionStatus.Offline,
                            connectionError = error.message ?: "Could not connect to the adapter",
                        )
                    }
                    ServiceCompat.stopForeground(
                        this@ObdMonitorService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE,
                    )
                    stopSelf()
                }
        }
    }

    private fun createObdConnectionStack(
        endpoint: car.mazda.obd.android.core.elm.transport.AdapterEndpoint =
            requireNotNull(AdapterConnectionPreferences(this).load()),
    ) {
        val connectivityManager = requireNotNull(getSystemService(ConnectivityManager::class.java)) {
            "ConnectivityManager is required for OBD Wi-Fi routing"
        }
        client = OBDClient(WifiElmTransport(connectivityManager, endpoint))
        sessionManager = OBDSessionManager(
            client = client,
            scope = scope,
            requiredPids = pollingTargets.mapTo(mutableSetOf()) { it.request.pidCode },
        )
        pollingEngine = TelemetryPollingEngine(client = client, sessionManager = sessionManager)
    }

    private suspend fun observeSessionState() {
        sessionManager.sessionState
            .map { state ->
                when (state) {
                    is OBDSessionState.Idle -> MonitorConnectionStatus.Offline
                    is OBDSessionState.ConnectingSocket,
                    is OBDSessionState.InitializingEcu -> MonitorConnectionStatus.Connecting
                    is OBDSessionState.Reconnecting -> MonitorConnectionStatus.Reconnecting
                    is OBDSessionState.Ready -> MonitorConnectionStatus.Ready
                    is OBDSessionState.Error -> MonitorConnectionStatus.Offline
                }
            }
            .distinctUntilChanged()
            .collect { connectionStatus ->
                ObdMonitorStateStore.update {
                    it.copy(
                        connectionStatus = connectionStatus,
                        connectionError = if (connectionStatus == MonitorConnectionStatus.Offline) {
                            (sessionManager.sessionState.value as? OBDSessionState.Error)?.throwable?.message
                        } else null,
                    )
                }
            }
    }

    private suspend fun connectInitialSession() {
        tripSummaryRepository.refreshRecentTrips()
        val reconnecting = nextConnectionIsReconnect
        nextConnectionIsReconnect = false
        sessionManager.connectUntilReady(reconnecting = reconnecting)
    }

    private suspend fun observeTripState() {
        tripStateManager.tripState.collect { state ->
            tripSummaryTracker.onTripStateChanged(state)?.let { summary ->
                tripSummaryRepository.saveTrip(summary)
                ObdMonitorStateStore.update {
                    it.copy(tripSummaryVersion = it.tripSummaryVersion + 1)
                }
            }
            ObdMonitorStateStore.update { it.copy(activeTrip = tripSummaryTracker.activeTrip.value) }
            if (canRecordRoute()) {
                routeRecorder.onTripStateChanged(state, tripSummaryTracker.activeTrip.value?.startedAtMs)
            } else {
                routeRecorder.stop()
            }

        }
    }

    private suspend fun observeTelemetryState() {
        pollingEngine.telemetryFlow(pollingTargets)
            .catch { t -> AppLogger.log("telemetryFlow error: ${t.message}") }
            .collect { result ->
                when (result.target.metric) {
                    TelemetryMetric.EngineRpm -> {
                        handleEngineRpm(responseMapper.mapEngineRpm(result.response))
                    }
                    TelemetryMetric.CoolantTemperature -> {
                        handleCoolantTemperature(responseMapper.mapEngineCoolantTemperature(result.response))
                    }
                }
            }
    }

    private suspend fun handleEngineRpm(sample: EngineRpmSample) {
        latestRpm = sample.displayRpm()
        ObdMonitorStateStore.update { it.copy(rpm = latestRpm) }
        if (sample is EngineRpmSample.Value) {
            tripSummaryTracker.onRpmChanged(sample.rpm)
            publishActiveTrip()
        }
        checkWarmupWarning()
    }

    private suspend fun handleCoolantTemperature(sample: EngineTemperatureSample) {
        latestCoolantTemp = sample.displayTemperature()
        ObdMonitorStateStore.update {
            it.copy(
                coolantTemp = latestCoolantTemp,
                warmupText = sample.warmupText(),
            )
        }
        tripSummaryTracker.onEngineTemperatureChanged(latestCoolantTemp)
        publishActiveTrip()
        checkWarmupWarning()
    }

    private fun publishActiveTrip() {
        ObdMonitorStateStore.update {
            it.copy(activeTrip = tripSummaryTracker.activeTrip.value)
        }
    }

    private suspend fun checkWarmupWarning() {
        when (warmupWarningManager.onEngineData(latestRpm, latestCoolantTemp)) {
            is WarmupWarning.HighRpmForTemperature -> {
                AppLogger.log("Play warmup warning sound")
                soundPlayer.playPattern(SoundPatterns.TripleShortAlert)
            }
            is WarmupWarning.Overheat -> {
                AppLogger.log("Play overheat warning sound")
                soundPlayer.playPattern(SoundPatterns.RapidAlert)
            }
            null -> Unit
        }
    }

    private fun buildNotification(state: ObdMonitorState): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ObdMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val coolant = state.coolantTemp?.let { "${it}C" } ?: "--"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Mazda OBD monitoring")
            .setContentText("${state.connectionText} | ${state.rpm} RPM | Coolant $coolant")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${state.connectionText}\nRPM: ${state.rpm}\nCoolant: $coolant\n${state.warmupText}")
            )
            .setContentIntent(openIntent)
            .addAction(R.mipmap.ic_launcher, "Stop", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OBD monitoring",
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun EngineRpmSample.displayRpm(): Int {
        val now = System.currentTimeMillis()
        return when (this) {
            is EngineRpmSample.Value -> {
                latestValidRpm = rpm
                latestValidRpmAtMs = now
                rpm
            }
            is EngineRpmSample.NoData,
            is EngineRpmSample.ConnectionError -> {
                if (now - latestValidRpmAtMs <= RPM_STALE_HOLD_MS) latestValidRpm else 0
            }
        }
    }

    private fun EngineTemperatureSample.displayTemperature(): Int? =
        when (this) {
            is EngineTemperatureSample.Value -> celsius
            is EngineTemperatureSample.NoData,
            is EngineTemperatureSample.ConnectionError -> null
        }

    private fun EngineTemperatureSample.warmupText(): String =
        when (this) {
            is EngineTemperatureSample.Value -> {
                val stage = EngineWarmupGuidance.stageFor(celsius)
                "Coolant ${celsius}C - ${stage.detail}"
            }
            is EngineTemperatureSample.NoData -> "Coolant temp: --"
            is EngineTemperatureSample.ConnectionError -> "Coolant temp: connection error"
        }

    companion object {
        private const val CHANNEL_ID = "obd_monitoring"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "car.mazda.obd.android.action.STOP_OBD_MONITOR"
        private const val ACTION_REFRESH_ROUTE_RECORDING = "car.mazda.obd.android.action.REFRESH_ROUTE_RECORDING"
        private const val ACTION_VALIDATE_SAVED_ENDPOINT =
            "car.mazda.obd.android.action.VALIDATE_SAVED_ENDPOINT"
        private const val ACTION_START_TRIP = "car.mazda.obd.android.action.START_TRIP"
        private const val ACTION_STOP_TRIP = "car.mazda.obd.android.action.STOP_TRIP"
        private const val RPM_STALE_HOLD_MS = 2_500L

        fun start(context: Context) {
            val intent = Intent(context, ObdMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ObdMonitorService::class.java).setAction(ACTION_STOP)
            )
        }

        fun refreshRouteRecording(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ObdMonitorService::class.java).setAction(ACTION_REFRESH_ROUTE_RECORDING),
            )
        }

        fun validateSavedEndpoint(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ObdMonitorService::class.java)
                    .setAction(ACTION_VALIDATE_SAVED_ENDPOINT),
            )
        }

        fun startTrip(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ObdMonitorService::class.java).setAction(ACTION_START_TRIP),
            )
        }

        fun stopTrip(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ObdMonitorService::class.java).setAction(ACTION_STOP_TRIP),
            )
        }
    }

    private fun startTrip() {
        startMonitoring()
        tripStateManager.startTrip()
    }

    private fun stopTrip() {
        tripStateManager.stopTrip()
    }

    private fun refreshRouteRecording() {
        startMonitoring()
        val allowed = canRecordRoute()
        startAsForeground(includeLocation = allowed)
        if (allowed) {
            routeRecorder.onTripStateChanged(
                tripStateManager.tripState.value,
                tripSummaryTracker.activeTrip.value?.startedAtMs,
            )
        } else {
            routeRecorder.stop()
        }
    }

    private fun canRecordRoute(): Boolean =
        routePreferences.recordingEnabled && (
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            )

    private fun startAsForeground(includeLocation: Boolean) {
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                if (includeLocation) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(ObdMonitorStateStore.state.value),
            types,
        )
    }
}
