package com.example.locationreminder.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.view.LayoutInflater
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager as LinearLayoutManagerImpl
import androidx.recyclerview.widget.RecyclerView as RecycleView
import com.example.locationreminder.ReminderAdapter
import com.example.locationreminder.data.Reminder

class ReminderDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "reminder_database"
        
        fun getInstance(context: Context): ReminderDatabase = ReminderDatabase(context.applicationContext)
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("CREATE TABLE reminders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "description TEXT DEFAULT '', " +
                "locationLat REAL NOT NULL, " +
                "locationLng REAL NOT NULL, " +
                "proximityRadiusMeters INTEGER NOT NULL, " +
                "is_active INTEGER DEFAULT 1, " +
                "createdAt INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS reminders")
        onCreate(db)
    }

    /** Extension function to get readable database */
    fun readable(): SQLiteDatabase? = this.readableDatabase
    
    /** Extension function to get writable database */
    fun writable(): SQLiteDatabase? = this.writableDatabase

    /** Get either readable or writable database */
    fun db(): SQLiteDatabase? = readable() ?: writable()

    /**
     * Get all reminders from database
     */
    fun getAllReminders(): List<Reminder> {
        val db = this.db() ?: throw IllegalStateException("Database not initialized")
        return db.query(
            "reminders",
            arrayOf("id", "title", "description", "locationLat", "locationLng", "proximityRadiusMeters", "is_active", "createdAt"),
            null,
            null,
            null,
            null,
            "id DESC"
        ).use { cursor ->
            val reminders = mutableListOf<Reminder>()
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                    val description = getStringOrNull(cursor, "description") ?: ""
                    val locationLat = cursor.getDouble(cursor.getColumnIndexOrThrow("locationLat"))
                    val locationLng = cursor.getDouble(cursor.getColumnIndexOrThrow("locationLng"))
                    val proximityRadiusMeters = cursor.getInt(cursor.getColumnIndexOrThrow("proximityRadiusMeters"))
                    val is_active = cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) != 0
                    val createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"))
                    reminders.add(Reminder(id, title, description, locationLat, locationLng, proximityRadiusMeters, is_active, createdAt))
                } while (cursor.moveToNext())
            }
            reminders
        }
    }

    /**
     * Insert a new reminder - replace existing if same location/radius and inactive
     */
    fun insert(reminder: Reminder): Long {
        val db = this.writable() ?: throw IllegalStateException("No writable database")
        // First check for existing record with same location and radius that is inactive
        var existingId: Long? = null
        
        val existingCursor = db.query(
            "reminders",
            arrayOf("id", "is_active"),
            "(locationLat = ? AND locationLng = ? AND proximityRadiusMeters = ?)",
            arrayOf(reminder.locationLat.toString(), reminder.locationLng.toString(), reminder.proximityRadiusMeters.toString()),
            null, null, null
        )
        
        existingCursor.use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val isActive = cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) != 0
                    if (!isActive && existingId == null) {
                        existingId = cursor.getLong(0)
                    } else if (isActive) {
                        // Active reminder with same location exists, abort insert
                        existingCursor.close()
                        return@insert -1L
                    }
                } while (cursor.moveToNext())
            }
        }
        
        // Deactivate existing inactive record if found
        existingId?.let { id ->
            db.update("reminders", android.content.ContentValues().apply { put("is_active", 0) }, "id = ?", arrayOf(id.toString()))
        }
        
        // Insert new record
        val values = android.content.ContentValues().apply {
            put("title", reminder.title)
            put("description", reminder.description)
            put("locationLat", reminder.locationLat)
            put("locationLng", reminder.locationLng)
            put("proximityRadiusMeters", reminder.proximityRadiusMeters)
            put("is_active", if (reminder.is_active) 1 else 0)
            put("createdAt", reminder.createdAt)
        }
        return db.insert("reminders", null, values) ?: throw IllegalStateException("Failed to insert reminder")
    }

    /**
     * Update existing reminder
     */
    fun update(reminder: Reminder) {
        val db = this.db() ?: return
        if (reminder.id <= 0) return
        
        db.update(
            "reminders",
            android.content.ContentValues().apply {
                put("title", reminder.title)
                put("description", reminder.description)
                put("locationLat", reminder.locationLat)
                put("locationLng", reminder.locationLng)
                put("proximityRadiusMeters", reminder.proximityRadiusMeters)
                put("is_active", if (reminder.is_active) 1 else 0)
            },
            "id = ?",
            arrayOf(reminder.id.toString())
        )
    }

    /**
     * Delete a reminder by id
     */
    fun delete(id: Long) {
        val db = this.db() ?: return
        db.delete("reminders", "id = ?", arrayOf(id.toString()))
    }

    /**
     * Get reminder by id
     */
    fun getReminderById(id: Long): Reminder? {
        val db = this.readable()
            ?: this.writable()
            ?: throw IllegalStateException("Database not initialized")
        
        return db.query(
            "reminders",
            arrayOf("id", "title", "description", "locationLat", "locationLng", "proximityRadiusMeters", "is_active", "createdAt"),
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                Reminder(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    description = getStringOrNull(cursor, "description") ?: "",
                    locationLat = cursor.getDouble(cursor.getColumnIndexOrThrow("locationLat")),
                    locationLng = cursor.getDouble(cursor.getColumnIndexOrThrow("locationLng")),
                    proximityRadiusMeters = cursor.getInt(cursor.getColumnIndexOrThrow("proximityRadiusMeters")),
                    is_active = cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) != 0,
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"))
                )
            } else null
        }
    }

    /** Helper function to get nullable string from cursor */
    private fun getStringOrNull(cursor: Cursor, columnName: String): String? {
        val index = cursor.getColumnIndex(columnName)
        return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
    }
}