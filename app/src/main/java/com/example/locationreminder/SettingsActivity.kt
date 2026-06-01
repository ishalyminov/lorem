package com.example.locationreminder

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.locationreminder.data.Reminder
import com.example.locationreminder.data.ReminderDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsActivity : AppCompatActivity() {

    private lateinit var db: ReminderDatabase
    companion object {
        private const val EXPORT_REQUEST_CODE = 1001
        private const val IMPORT_REQUEST_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        db = ReminderDatabase.getInstance(this)

        val rootLayout = findViewById<android.view.View>(R.id.rootLayout)
        val colorSets = listOf(
            intArrayOf(android.graphics.Color.parseColor("#E8EAF6"), android.graphics.Color.parseColor("#C5CAE9"), android.graphics.Color.parseColor("#D1C4E9")),
            intArrayOf(android.graphics.Color.parseColor("#F3E5F5"), android.graphics.Color.parseColor("#E1BEE7"), android.graphics.Color.parseColor("#EDE7F6")),
            intArrayOf(android.graphics.Color.parseColor("#E0F2F1"), android.graphics.Color.parseColor("#B2DFDB"), android.graphics.Color.parseColor("#C8E6C9")),
            intArrayOf(android.graphics.Color.parseColor("#E8EAF6"), android.graphics.Color.parseColor("#BBDEFB"), android.graphics.Color.parseColor("#C5CAE9")),
            intArrayOf(android.graphics.Color.parseColor("#FFF3E0"), android.graphics.Color.parseColor("#FFE0B2"), android.graphics.Color.parseColor("#FFCCBC")),
            intArrayOf(android.graphics.Color.parseColor("#FCE4EC"), android.graphics.Color.parseColor("#F8BBD0"), android.graphics.Color.parseColor("#E1BEE7"))
        )
        val gradientDrawable = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR, colorSets[0]
        )
        rootLayout.background = gradientDrawable
        var animCycle = 0
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 12000
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                val i = animCycle % colorSets.size
                val sc = colorSets[i]
                val ec = colorSets[(i + 1) % colorSets.size]
                gradientDrawable.colors = intArrayOf(
                    android.animation.ArgbEvaluator().evaluate(f, sc[0], ec[0]) as Int,
                    android.animation.ArgbEvaluator().evaluate(f, sc[1], ec[1]) as Int,
                    android.animation.ArgbEvaluator().evaluate(f, sc[2], ec[2]) as Int
                )
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(p0: android.animation.Animator) {}
                override fun onAnimationEnd(p0: android.animation.Animator) {}
                override fun onAnimationCancel(p0: android.animation.Animator) {}
                override fun onAnimationRepeat(p0: android.animation.Animator) { animCycle++ }
            })
            start()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExport).setOnClickListener {
            launchExport()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnImport).setOnClickListener {
            launchImport()
        }
    }

    private fun launchExport() {
        val reminders = db.getAllReminders()
        if (reminders.isEmpty()) {
            Toast.makeText(this, "No reminders to export", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = "lorem_reminders.json"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        startActivityForResult(intent, EXPORT_REQUEST_CODE)
    }

    private fun launchImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, IMPORT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        val uri = data.data ?: return
        when (requestCode) {
            EXPORT_REQUEST_CODE -> exportToFile(uri)
            IMPORT_REQUEST_CODE -> importFromFile(uri)
        }
    }

    private fun exportToFile(uri: Uri) {
        try {
            val reminders = db.getAllReminders()
            val jsonArray = JSONArray()
            for (r in reminders) {
                val obj = JSONObject().apply {
                    put("title", r.title)
                    put("locationLat", r.locationLat)
                    put("locationLng", r.locationLng)
                    put("proximityRadiusMeters", r.proximityRadiusMeters)
                    put("locationName", r.locationName)
                    put("is_active", r.is_active)
                    put("createdAt", r.createdAt)
                }
                jsonArray.put(obj)
            }
            val json = JSONObject().apply { put("reminders", jsonArray) }
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toString(2).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "Exported ${reminders.size} reminder(s)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importFromFile(uri: Uri) {
        try {
            val jsonText = contentResolver.openInputStream(uri)?.use { istream ->
                BufferedReader(InputStreamReader(istream, Charsets.UTF_8)).use { reader ->
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    sb.toString()
                }
            } ?: throw IllegalStateException("Cannot read file")

            val json = JSONObject(jsonText)
            val jsonArray = json.getJSONArray("reminders")
            var imported = 0
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val reminder = Reminder(
                    id = 0,
                    title = obj.getString("title"),
                    locationLat = obj.getDouble("locationLat"),
                    locationLng = obj.getDouble("locationLng"),
                    proximityRadiusMeters = obj.optInt("proximityRadiusMeters", 100),
                    locationName = obj.optString("locationName", ""),
                    is_active = obj.optBoolean("is_active", true),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
                val result = db.insert(reminder)
                if (result > 0) imported++
            }
            Toast.makeText(this, "Imported $imported reminder(s)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
