package com.example.locationreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView as RecycleView
import com.example.locationreminder.data.ReminderDatabase
import com.example.locationreminder.data.Reminder

class MainActivity : AppCompatActivity(), ReminderAdapter.ReminderClickListener {

    private lateinit var recyclerView: RecycleView
    private lateinit var adapter: ReminderAdapter
    private lateinit var db: ReminderDatabase
    private var allReminders: List<Reminder> = emptyList()
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val REQUEST_NOTIFICATION_PERMISSION = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = ReminderDatabase.getInstance(this)
        
        recyclerView = findViewById(R.id.remindersRecyclerView)
        // Set up LinearLayoutManager for vertical scrolling list
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        
        // Initialize and set up FAB in lower right corner
        val addButton = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addReminderButton)
        addButton.setOnClickListener { showAddReminderDialog() }

        loadReminders()

        requestLocationPermission()
    }

    private fun loadReminders() {
        allReminders = db.getAllReminders()
        adapter = ReminderAdapter(this)
        adapter.submitList(allReminders)
        recyclerView.adapter = adapter
    }

    private fun requestLocationPermission() {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            requestNotificationPermission()
        }
    }

    private fun requestNotificationPermission() {
        val notificationPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null

        notificationPermission?.let {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(it), REQUEST_NOTIFICATION_PERMISSION)
            }
        } ?: showNotificationEnabledDialog()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    requestNotificationPermission()
                } else {
                    Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_NOTIFICATION_PERMISSION -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    showNotificationEnabledDialog()
                }
            }
        }
    }

    private fun showAddReminderDialog() {
        val view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_add_reminder, null)
        
        AlertDialog.Builder(this)
            .setTitle("Add Location Reminder")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                saveReminderFromDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveReminderFromDialog() {
        val inputTitle: android.widget.EditText = findViewById(R.id.inputTitle)
        val inputDescription: android.widget.EditText = findViewById(R.id.inputDescription)
        val inputLocationLat: android.widget.EditText = findViewById(R.id.inputLocationLat)
        val inputLocationLng: android.widget.EditText = findViewById(R.id.inputLocationLng)
        val inputRadius: android.widget.EditText = findViewById(R.id.inputRadius)

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

        if (title.isNotEmpty()) {
            val lat = latText.toDoubleOrNull() ?: return
            val lng = lngText.toDoubleOrNull() ?: return
            val radius = radiusText.toIntOrNull() ?: return

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
            
            Toast.makeText(this, "Reminder added", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNotificationEnabledDialog() {
        val intent = Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
            putExtra("app_package", packageName)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Notifications Required")
            .setMessage("Please enable notifications to receive location reminders.")
            .setPositiveButton("Enable") { _, _ ->
                startActivity(intent)
            }
            .setNegativeButton("OK") { _, _ -> }
            .show()
    }

    override fun onReminderClick(reminder: Reminder) {
        Toast.makeText(this, "Selected: ${reminder.title}", Toast.LENGTH_SHORT).show()
    }
}