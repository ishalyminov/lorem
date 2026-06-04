package com.shalyminov.lorem.data

data class Reminder(
    val id: Long = 0,
    val title: String,
    val locationLat: Double,
    val locationLng: Double,
    val proximityRadiusMeters: Int,
    val locationName: String = "",
    val is_active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
