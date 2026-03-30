package com.example.locationreminder

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView as RecycleView
import com.example.locationreminder.R
import com.example.locationreminder.ReminderAdapter
import com.example.locationreminder.data.Reminder
import com.example.locationreminder.data.ReminderDatabase

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecycleView
    private lateinit var db: ReminderDatabase
    private lateinit var adapter: ReminderAdapter
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = ReminderDatabase.getInstance(this)
        
        recyclerView = findViewById(R.id.remindersRecyclerView)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        
        // Create and set up adapter with the database instance
        adapter = ReminderAdapter(listener = null, database = db)
        recyclerView.adapter = adapter
        
        // Initialize and set up FAB in lower right corner
        val addButton = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addReminderButton)
        addButton.setOnClickListener { showAddReminderDialog() }

        loadReminders()
        
        // Start location monitoring service
        startLocationMonitoring()
    }

    private fun loadReminders() {
        val reminders = db.getAllReminders().filter { it.is_active }.sortedByDescending { it.createdAt }
        adapter.submitList(reminders, this)
    }

    private fun startLocationMonitoring() {
        val intent = Intent(this, LocationMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (checkLocationPermissions()) {
                startForegroundService(intent)
            } else {
                Toast.makeText(this, "Please enable location permissions in settings", Toast.LENGTH_LONG).show()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (checkLocationPermissions()) {
                startService(intent)
            } else {
                Toast.makeText(this, "Please enable location permissions in settings", Toast.LENGTH_LONG).show()
            }
        } else {
            // Android 6.0 and below - permissions are granted at install time
            startService(intent)
        }
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    loadReminders()
                    startLocationMonitoring()
                } else {
                    Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showAddReminderDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_reminder, null)
        
        // Get current location for prefilled values
        getCurrentLocation()?.let { (lat, lng) ->
            view.findViewById<android.widget.EditText>(R.id.inputLocationLat).setText("${String.format("%.6f", lat)}")
            view.findViewById<android.widget.EditText>(R.id.inputLocationLng).setText("${String.format("%.6f", lng)}")
        }
        
        // Prefill with dummy title and description for user convenience
        val dummyTitle = "My Location Reminder"
        val dummyDescription = "A reminder about my current location"
        view.findViewById<android.widget.EditText>(R.id.inputTitle).setText(dummyTitle)
        view.findViewById<android.widget.EditText>(R.id.inputDescription).setText(dummyDescription)

        AlertDialog.Builder(this)
            .setTitle("Add Location Reminder")
            .setView(view)
            .setPositiveButton("Save") { _, _ -> saveReminderFromDialog(view) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getCurrentLocation(): Pair<Double, Double>? {
        // Note: This method requires location permissions to be granted at runtime
        return null // Return null to force user to enter coordinates manually
    }

    private fun saveReminderFromDialog(formView: android.view.View) {
        val inputTitle = formView.findViewById(R.id.inputTitle) as android.widget.EditText
        val inputDescription = formView.findViewById(R.id.inputDescription) as android.widget.EditText
        val inputLocationLat = formView.findViewById(R.id.inputLocationLat) as android.widget.EditText
        val inputLocationLng = formView.findViewById(R.id.inputLocationLng) as android.widget.EditText
        val inputRadius = formView.findViewById(R.id.inputRadius) as android.widget.EditText

        inputTitle.hint = "Title"
        inputDescription.hint = "Description (optional)"
        inputLocationLat.hint = "Latitude"
        inputLocationLng.hint = "Longitude"
        inputRadius.hint = "Radius (meters)"

        val title = inputTitle.text.toString().trim()
        val description = inputDescription.text.toString().trim()
        val latText = inputLocationLat.text.toString().trim()
        val lngText = inputLocationLng.text.toString().trim()
        val radiusText = inputRadius.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show()
            return
        }

        val latTextTrimmed = latText.trim()
        val lngTextTrimmed = lngText.trim()
        val radiusTextTrimmed = radiusText.trim()

        if (latTextTrimmed.isBlank()) {
            Toast.makeText(this, "Please enter a valid latitude", Toast.LENGTH_SHORT).show()
            return
        }

        if (lngTextTrimmed.isBlank()) {
            Toast.makeText(this, "Please enter a valid longitude", Toast.LENGTH_SHORT).show()
            return
        }

        if (radiusTextTrimmed.isBlank()) {
            Toast.makeText(this, "Please enter a valid radius in meters", Toast.LENGTH_SHORT).show()
            return
        }

        val lat = latTextTrimmed.toDoubleOrNull() ?: 0.0
        val lng = lngTextTrimmed.toDoubleOrNull() ?: 0.0
        val radius = radiusTextTrimmed.toIntOrNull() ?: 1000 // Default to 1000m if invalid

        try {
            db.insert(Reminder(
                id = 0,
                title = title,
                description = description,
                locationLat = lat,
                locationLng = lng,
                proximityRadiusMeters = radius,
                is_active = true,
                createdAt = System.currentTimeMillis()
            ))

            loadReminders()
            
            Toast.makeText(this, "Reminder added. Location tracking started.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving reminder", Toast.LENGTH_LONG).show()
        }
    }
}