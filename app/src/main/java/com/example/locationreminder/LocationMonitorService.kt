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
import java.util.concurrent.Executors

class LocationMonitorService : Service() {

    private val db: ReminderDatabase by lazy { ReminderDatabase(this) }
    private var lastKnownLocation: android.location.Location? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("location_updates", "Location Updates", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val notification = createNotification("Monitoring Locations", "Tracking your current location for reminders")
        startForeground(1, notification)
        
        handler = Handler()
        processRunnable = object : Runnable { override fun run() { checkAndProcessLocation() } }
        handler?.postDelayed(processRunnable!!, 2000L)
        tryGetLocationAndUpdateTracking()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacks(processRunnable!!)
        handler = null
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(1)
    }

    private fun checkAndProcessLocation() {
        executor.execute {
            try {
                lastKnownLocation?.let { location -> checkForReminderTriggers(location) }
            } catch (e: Exception) { e.printStackTrace() }
            handler?.postDelayed(processRunnable!!, 2000L)
        }
    }

    private fun tryGetLocationAndUpdateTracking() {
        executor.execute {
            try {
                fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
                    if (location != null) {
                        lastKnownLocation = location
                        checkForReminderTriggers(location)
                        startContinuousTracking()
                    } else {
                        handler?.postDelayed(processRunnable!!, 2000L)
                    }
                }?.addOnFailureListener { e -> e.printStackTrace(); handler?.postDelayed(processRunnable!!, 2000L) }
            } catch (e: Exception) { e.printStackTrace(); handler?.postDelayed(processRunnable!!, 2000L) }
        }
    }

    private fun startContinuousTracking() {
        val locationRequest = LocationRequest.Builder(16, 2000).build()
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                result.lastLocation?.let { lastKnownLocation = it }
                checkForReminderTriggers(lastKnownLocation!!)
            }
            override fun onLocationAvailability(availability: LocationAvailability) {
                super.onLocationAvailability(availability)
                if (!availability.isLocationAvailable) handler?.postDelayed(processRunnable!!, 2000L)
            }
        }
        try {
            fusedLocationClient?.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
            handler?.postDelayed(processRunnable!!, 2000L)
        }
    }

    private fun createNotification(contentText: String, subText: String): android.app.Notification {
        return NotificationCompat.Builder(this, "location_updates")
            .setContentTitle("Location Reminder Service").setSubText("$contentText - $subText")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent).setOngoing(true).build()
    }

    private fun checkForReminderTriggers(location: android.location.Location) {
        val activeReminders = db.getAllReminders().filter { it.is_active }
        for (reminder in activeReminders) {
            val distance = calculateDistanceBetweenTwoPoints(
                location.latitude, location.longitude, reminder.locationLat, reminder.locationLng)
            val now = System.currentTimeMillis() / 1000L
            val lastTriggerTime = triggerTimes[reminder.id] ?: 0L
            if (distance <= reminder.proximityRadiusMeters && (now - lastTriggerTime) >= 60) {
                sendTriggerNotification(reminder, distance)
                triggerTimes[reminder.id] = now
            }
        }
    }

    private fun calculateDistanceBetweenTwoPoints(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rlat1 = Math.toRadians(lat1).toDouble()
        val rlon1 = Math.toRadians(lon1).toDouble()
        val rlat2 = Math.toRadians(lat2).toDouble()
        val rlon2 = Math.toRadians(lon2).toDouble()
        val dlon = rlon2 - rlon1
            val sinHalfLonSq: Double = Math.pow(Math.sin(dlon / 2.0).toDouble(), 2.0)
            val sinHalfLatSq: Double = Math.pow(Math.sin((rlat2 - rlat1) / 2.0).toDouble(), 2.0)
        val a: Double = sinHalfLonSq + (Math.cos(rlat1) * Math.cos(rlat2) * sinHalfLatSq)
        return (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)) * 6371000.0)
    }

    private fun sendTriggerNotification(reminder: Reminder, distanceInMeters: Double) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) android.os.Process.myPid() + reminder.id.toInt() else reminder.id.toInt()
        val title = "${reminder.title}!"
        val sub = String.format("You are %.1fm from the target location.", distanceInMeters.toDouble())
        val notification = NotificationCompat.Builder(this, "location_updates")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title).setSubText(sub)
            .setAutoCancel(true).setContentIntent(pendingIntent).build()
        notificationManager.notify(notificationId, notification)
    }

    companion object {
        private val triggerTimes: MutableMap<Long, Long> = java.util.concurrent.ConcurrentHashMap()
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