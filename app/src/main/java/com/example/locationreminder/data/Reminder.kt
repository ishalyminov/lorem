package com.example.locationreminder.data

data class Reminder(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val locationLat: Double,
    val locationLng: Double,
    val proximityRadiusMeters: Int,
    val is_active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)