package com.example.locationreminder

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView as RecycleView
import com.example.locationreminder.R
import com.example.locationreminder.ReminderAdapter
import com.example.locationreminder.data.Reminder
import com.example.locationreminder.data.ReminderDatabase
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest

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
        adapter = ReminderAdapter(listener = null, database = db, onDeleteCallback = { loadReminders() })
        recyclerView.adapter = adapter

        // Initialize and set up FAB in lower right corner
        val addButton = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addReminderButton)
        addButton.setOnClickListener { showAddReminderDialog() }

        loadReminders()

        // Request location permissions if not granted
        if (!checkLocationPermissions()) {
            requestLocationPermissions()
        } else {
            // Start location monitoring service
            startLocationMonitoring()
        }
    }

    private fun loadReminders() {
        val reminders = db.getAllReminders().filter { it.is_active }.sortedByDescending { it.createdAt }
        adapter.submitList(reminders, this)
    }

    private fun requestLocationPermissions() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
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
                    Toast.makeText(this, "Location permission required for full functionality", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showAddReminderDialog() {
        // Initialize Places API if not already initialized
        if (!Places.isInitialized()) {
            val apiKey = getMapsApiKey()
            android.util.Log.d("MainActivity", "Initializing Places with API key: ${if (apiKey.isNotEmpty()) "FOUND (${apiKey.take(10)}...)" else "NOT FOUND"}")
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Google Maps API key not found. Address search will not work.", Toast.LENGTH_LONG).show()
            }
            // Use the new Places API
            try {
                Places.initialize(applicationContext, apiKey)
                android.util.Log.d("MainActivity", "Places API initialized successfully")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to initialize Places API", e)
                Toast.makeText(this, "Failed to initialize Places API: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        val placesClient = Places.createClient(this)

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_reminder, null)

        // Prefill with dummy title and description for user convenience
        val dummyTitle = "My Location Reminder"
        val dummyDescription = "A reminder about my current location"
        view.findViewById<android.widget.EditText>(R.id.inputTitle).setText(dummyTitle)
        view.findViewById<android.widget.EditText>(R.id.inputDescription).setText(dummyDescription)

        // Set default radius
        view.findViewById<android.widget.EditText>(R.id.inputRadius).setText("100")

        // Get MapView and set up the map
        val mapView = view.findViewById<MapView>(R.id.mapPreview)
        val loadingProgress = view.findViewById<android.widget.ProgressBar>(R.id.mapLoadingProgress)
        val locationStatusText = view.findViewById<android.widget.TextView>(R.id.locationStatusText)

        // Track current location marker
        var currentMarker: Marker? = null
        var currentLocation: Pair<Double, Double>? = null

        // Set up address search autocomplete
        val addressSearch = view.findViewById<android.widget.AutoCompleteTextView>(R.id.inputAddressSearch)
        val addressAdapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_dropdown_item_1line) {
            private val customFilter = object : Filter() {
                override fun performFiltering(constraint: CharSequence?): Filter.FilterResults {
                    val results = Filter.FilterResults()
                    results.values = listOf<String>()
                    results.count = 0
                    return results
                }
                override fun publishResults(constraint: CharSequence?, results: Filter.FilterResults?) {}
            }
            override fun getFilter(): Filter = customFilter
        }
        addressSearch.setAdapter(addressAdapter)

        // Set dropdown background to transparent
        addressSearch.setDropDownBackgroundDrawable(getDrawable(R.drawable.autocomplete_dropdown_bg))
        
        addressSearch.setOnItemClickListener { parent, _, position, _ ->
            val selectedAddress = parent.getItemAtPosition(position) as String
            // Fetch place details for the selected address
            fetchPlaceDetails(placesClient, selectedAddress, addressAdapter) { place ->
                if (place != null) {
                    currentLocation = Pair(place.latLng?.latitude ?: 0.0, place.latLng?.longitude ?: 0.0)
                    
                    // Update hidden fields
                    view.findViewById<android.widget.EditText>(R.id.inputLocationLat).setText(
                        String.format("%.6f", place.latLng?.latitude ?: 0.0)
                    )
                    view.findViewById<android.widget.EditText>(R.id.inputLocationLng).setText(
                        String.format("%.6f", place.latLng?.longitude ?: 0.0)
                    )
                    
                    // Update map
                    mapView.getMapAsync { googleMap ->
                        val latLng = place.latLng
                        if (latLng != null) {
                            // Update marker position
                            if (currentMarker != null) {
                                currentMarker?.position = latLng
                            } else {
                                currentMarker = googleMap.addMarker(
                                    MarkerOptions()
                                        .position(latLng)
                                        .title("Reminder Location")
                                )
                            }
                            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                            
                            // Hide loading, show map
                            loadingProgress.visibility = android.view.View.GONE
                            locationStatusText.visibility = android.view.View.GONE
                            mapView.visibility = android.view.View.VISIBLE
                        }
                    }
                }
            }
        }
        
        addressSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && s.length >= 2) {
                    searchAddresses(placesClient, s.toString(), addressAdapter)
                }
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Create dialog first so we can manage MapView lifecycle
        val dialog = AlertDialog.Builder(this, R.style.TransparentAlertDialog)
            .setTitle("Add Location Reminder")
            .setView(view)
            .setPositiveButton("Save") { _, _ -> saveReminderFromDialog(view) }
            .setNegativeButton("Cancel", null)
            .create()

        // Set dialog window to be transparent
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Initialize MapView
        mapView.onCreate(null)
        mapView.getMapAsync(OnMapReadyCallback { googleMap ->
            // Configure the map
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = false
            googleMap.uiSettings.isMapToolbarEnabled = false

            // Get current location and show on map
            if (checkLocationPermissions()) {
                val location = getCurrentLocation()
                if (location != null) {
                    currentLocation = location
                    val latLng = LatLng(location.first, location.second)

                    // Update hidden fields
                    view.findViewById<android.widget.EditText>(R.id.inputLocationLat).setText(String.format("%.6f", location.first))
                    view.findViewById<android.widget.EditText>(R.id.inputLocationLng).setText(String.format("%.6f", location.second))

                    // Add marker and move camera
                    currentMarker = googleMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Reminder Location")
                    )
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))

                    // Hide loading, show map
                    loadingProgress.visibility = android.view.View.GONE
                    locationStatusText.visibility = android.view.View.GONE
                    mapView.visibility = android.view.View.VISIBLE
                } else {
                    // Show error state
                    loadingProgress.visibility = android.view.View.GONE
                    locationStatusText.text = "Unable to get location. Use the search box above to find an address."
                    locationStatusText.visibility = android.view.View.VISIBLE
                }
            } else {
                // Request permissions
                requestLocationPermissions()
                loadingProgress.visibility = android.view.View.GONE
                locationStatusText.text = "Location permission required. Use the search box above to find an address."
                locationStatusText.visibility = android.view.View.VISIBLE
            }

            // Allow user to tap on map to change location
            googleMap.setOnMapClickListener { latLng ->
                currentLocation = Pair(latLng.latitude, latLng.longitude)
                view.findViewById<android.widget.EditText>(R.id.inputLocationLat).setText(String.format("%.6f", latLng.latitude))
                view.findViewById<android.widget.EditText>(R.id.inputLocationLng).setText(String.format("%.6f", latLng.longitude))

                // Update marker position
                if (currentMarker != null) {
                    currentMarker?.position = latLng
                } else {
                    currentMarker = googleMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Reminder Location")
                    )
                }
                
                // Clear the address search field since location was changed manually
                addressSearch.setText("")
            }
        })


        dialog.show()

        // Store dialog reference for lifecycle management
        mapView.setTag(dialog)
    }
    
    private fun getMapsApiKey(): String {
        // Read API key from manifest metadata
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            val metaData = appInfo.metaData
            return metaData?.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) {
            // Fallback: Try to read from properties file
            val keyFile = java.io.File("${filesDir.parentFile?.parentFile}/keystore/maps_api_key.properties")
            if (keyFile.exists()) {
                val props = java.util.Properties()
                keyFile.inputStream().use { props.load(it) }
                return props.getProperty("GOOGLE_MAPS_API_KEY", "")
            }
        }
        return ""
    }
    
    private fun searchAddresses(
        placesClient: com.google.android.libraries.places.api.net.PlacesClient,
        query: String,
        adapter: ArrayAdapter<String>
    ) {
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .build()
        
        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val predictions = response.autocompletePredictions
                android.util.Log.d("MainActivity", "Found ${predictions.size} predictions")
                runOnUiThread {
                    adapter.clear()
                    predictions.forEach { prediction ->
                        android.util.Log.d("MainActivity", "Prediction: ${prediction.getFullText(null)}")
                        adapter.add(prediction.getFullText(null).toString())
                    }
                    adapter.notifyDataSetChanged()
                    android.util.Log.d("MainActivity", "Adapter count: ${adapter.count}")
                }
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("MainActivity", "Error fetching predictions", exception)
                runOnUiThread {
                    Toast.makeText(this, "Search error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun fetchPlaceDetails(
        placesClient: com.google.android.libraries.places.api.net.PlacesClient,
        addressText: String,
        adapter: ArrayAdapter<String>,
        callback: (Place?) -> Unit
    ) {
        // First find the prediction that matches the selected address
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(addressText)
            .build()
        
        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val matchingPrediction = response.autocompletePredictions.find { 
                    it.getFullText(null).toString() == addressText 
                }
                
                if (matchingPrediction != null) {
                    // Fetch the place details including coordinates
                    val placeFields = listOf(Place.Field.LAT_LNG, Place.Field.NAME)
                    val fetchPlaceRequest = FetchPlaceRequest.newInstance(matchingPrediction.placeId, placeFields)
                    
                    placesClient.fetchPlace(fetchPlaceRequest)
                        .addOnSuccessListener { fetchResponse ->
                            callback(fetchResponse.place)
                        }
                        .addOnFailureListener {
                            callback(null)
                        }
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    private fun getCurrentLocation(): Pair<Double, Double>? {
        if (!checkLocationPermissions()) {
            return null
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var bestLocation: android.location.Location? = null

        try {
            val providers = locationManager.getProviders(true)
            for (provider in providers) {
                val location = locationManager.getLastKnownLocation(provider)
                if (location != null) {
                    if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                        bestLocation = location
                    }
                }
            }
        } catch (e: SecurityException) {
            return null
        } catch (e: Exception) {
            return null
        }

        return bestLocation?.let { Pair(it.latitude, it.longitude) }
    }

    private fun saveReminderFromDialog(formView: android.view.View) {
        val inputTitle = formView.findViewById(R.id.inputTitle) as android.widget.EditText
        val inputDescription = formView.findViewById(R.id.inputDescription) as android.widget.EditText
        val inputLocationLat = formView.findViewById(R.id.inputLocationLat) as android.widget.EditText
        val inputLocationLng = formView.findViewById(R.id.inputLocationLng) as android.widget.EditText
        val inputRadius = formView.findViewById(R.id.inputRadius) as android.widget.EditText

        val title = inputTitle.text.toString().trim()
        val description = inputDescription.text.toString().trim()
        val latText = inputLocationLat.text.toString().trim()
        val lngText = inputLocationLng.text.toString().trim()
        val radiusText = inputRadius.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (latText.isBlank()) {
            Toast.makeText(this, "Please select a location on the map", Toast.LENGTH_SHORT).show()
            return
        }

        if (lngText.isBlank()) {
            Toast.makeText(this, "Please select a location on the map", Toast.LENGTH_SHORT).show()
            return
        }

        if (radiusText.isBlank()) {
            Toast.makeText(this, "Please enter a valid radius in meters", Toast.LENGTH_SHORT).show()
            return
        }

        val lat = latText.toDoubleOrNull() ?: 0.0
        val lng = lngText.toDoubleOrNull() ?: 0.0
        val radius = radiusText.toIntOrNull() ?: 1000 // Default to 1000m if invalid

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