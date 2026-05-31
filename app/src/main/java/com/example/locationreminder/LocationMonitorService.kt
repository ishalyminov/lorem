package com.example.locationreminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.locationreminder.data.Reminder
import com.example.locationreminder.data.ReminderDatabase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.Executors

class LocationMonitorService : Service() {

    private val db: ReminderDatabase by lazy { ReminderDatabase(this) }
    private var lastKnownLocation: android.location.Location? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    private val pendingIntent by lazy {
        PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            }
        )
    }

    private val executor: java.util.concurrent.ExecutorService = Executors.newSingleThreadExecutor()
    private var handler: Handler? = null
    private var processRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val fgChannel = NotificationChannel(
                CHANNEL_FOREGROUND,
                "Location Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when location monitoring is active"
                setShowBadge(false)
            }
            nm.createNotificationChannel(fgChannel)

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Reminder Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when you are near a reminder location"
                enableVibration(true)
                enableLights(true)
            }
            nm.createNotificationChannel(alertChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        android.util.Log.i(TAG, "onStartCommand")

        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification("Starting location monitoring..."))

        handler = Handler(Looper.getMainLooper())
        processRunnable = object : Runnable {
            override fun run() { checkAndProcessLocation() }
        }

        tryGetLocationAndUpdateTracking()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacksAndMessages(null)
        handler = null
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(FOREGROUND_NOTIFICATION_ID)
    }

    private fun tryGetLocationAndUpdateTracking() {
        executor.execute {
            fusedLocationClient?.let { client ->
                android.util.Log.d(TAG, "Requesting last known location...")
                client.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        android.util.Log.i(TAG, "Got last location: (${location.latitude}, ${location.longitude})")
                        lastKnownLocation = location
                        checkForReminderTriggers(location)
                        updateForegroundNotification()
                    }
                    startContinuousTracking()
                }.addOnFailureListener { e ->
                    android.util.Log.e(TAG, "Failed to get last location", e)
                    startContinuousTracking()
                }
            } ?: run {
                android.util.Log.e(TAG, "fusedLocationClient is null!")
                handler?.postDelayed(processRunnable!!, 5000L)
            }
        }
    }

    private fun startContinuousTracking() {
        android.util.Log.i(TAG, "Starting continuous location tracking")
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                result.lastLocation?.let {
                    lastKnownLocation = it
                    android.util.Log.d(TAG, "Location update: (${it.latitude}, ${it.longitude})")
                }
                lastKnownLocation?.let {
                    checkForReminderTriggers(it)
                    updateForegroundNotification()
                }
            }
            override fun onLocationAvailability(availability: LocationAvailability) {
                super.onLocationAvailability(availability)
                if (!availability.isLocationAvailable) {
                    android.util.Log.w(TAG, "Location not available")
                    updateForegroundNotification("Location unavailable — waiting...")
                }
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            android.util.Log.i(TAG, "Location updates requested successfully")
            updateForegroundNotification()
        } catch (e: SecurityException) {
            android.util.Log.e(TAG, "SecurityException requesting location updates", e)
            updateForegroundNotification("Location permission denied")
        }
    }

    private fun checkAndProcessLocation() {
        executor.execute {
            try {
                lastKnownLocation?.let { location -> checkForReminderTriggers(location) }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in checkAndProcessLocation", e)
            }
            handler?.postDelayed(processRunnable!!, 10000L)
        }
    }

    private fun buildForegroundNotification(statusText: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_FOREGROUND)
            .setContentTitle("Location Reminder Active")
            .setContentText("Location reminder active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateForegroundNotification(customStatus: String? = null) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val status = customStatus ?: buildStatusText()
            nm.notify(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(status))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to update foreground notification", e)
        }
    }

    private fun buildStatusText(): String {
        val loc = lastKnownLocation
        if (loc == null) return "Waiting for location..."
        val activeReminders = try { db.getAllReminders().filter { it.is_active } } catch (_: Exception) { emptyList() }
        if (activeReminders.isEmpty()) return "No active reminders"

        var closestDist = Double.MAX_VALUE
        var closestName = ""
        for (r in activeReminders) {
            val d = calculateDistanceBetweenTwoPoints(loc.latitude, loc.longitude, r.locationLat, r.locationLng)
            if (d < closestDist) {
                closestDist = d
                closestName = r.title
            }
        }
        return "Nearest: \"${closestName}\" — ${String.format("%.0f", closestDist)}m away"
    }

    private fun checkForReminderTriggers(location: android.location.Location?) {
        if (location == null) {
            android.util.Log.w(TAG, "checkForReminderTriggers called with null location")
            return
        }
        try {
            val activeReminders = db.getAllReminders().filter { it.is_active }
            android.util.Log.d(TAG, "Checking ${activeReminders.size} active reminders at (${location.latitude}, ${location.longitude})")

        for (reminder in activeReminders) {
            val distance = calculateDistanceBetweenTwoPoints(
                location.latitude, location.longitude, reminder.locationLat, reminder.locationLng
            )
            android.util.Log.d(TAG, "Reminder '${reminder.title}' at (${reminder.locationLat}, ${reminder.locationLng}), radius=${reminder.proximityRadiusMeters}m, distance=${String.format("%.1f", distance)}m")

            val inside = distance <= reminder.proximityRadiusMeters
            if (inside && !insideRadius.contains(reminder.id)) {
                android.util.Log.i(TAG, "TRIGGERING '${reminder.title}' — distance ${String.format("%.1f", distance)}m within radius ${reminder.proximityRadiusMeters}m")
                sendTriggerNotification(reminder, distance)
                insideRadius.add(reminder.id)
            } else if (!inside && insideRadius.contains(reminder.id)) {
                insideRadius.remove(reminder.id)
                android.util.Log.i(TAG, "Left radius for '${reminder.title}' — cooldown cleared, distance ${String.format("%.1f", distance)}m")
            }
        }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in checkForReminderTriggers", e)
        }
    }

    private fun calculateDistanceBetweenTwoPoints(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rlat1 = Math.toRadians(lat1)
        val rlat2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.pow(Math.sin(dLat / 2.0), 2.0) + Math.cos(rlat1) * Math.cos(rlat2) * Math.pow(Math.sin(dLon / 2.0), 2.0)
        return 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)) * 6371000.0
    }

    private fun sendTriggerNotification(reminder: Reminder, distanceInMeters: Double) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = 1000 + reminder.id.toInt()
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(reminder.title)
            .setContentText(reminder.locationName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.locationName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        nm.notify(notificationId, notification)
        android.util.Log.i(TAG, "Notification sent for '${reminder.title}' (id=$notificationId)")
    }

    companion object {
        private const val TAG = "LocationMonitor"
        private const val CHANNEL_FOREGROUND = "location_monitoring"
        private const val CHANNEL_ALERTS = "reminder_alerts"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private val insideRadius: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap<Long, Unit>().keySet(Unit)

        fun startLocationMonitoring(context: Context) {
            val intent = Intent(context, LocationMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
        fun stopLocationMonitoring(context: Context) {
            val intent = Intent(context, LocationMonitorService::class.java)
            context.stopService(intent)
        }
    }
}
