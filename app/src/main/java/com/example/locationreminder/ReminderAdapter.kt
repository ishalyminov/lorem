package com.example.locationreminder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView as RecycleView
import com.example.locationreminder.data.Reminder
import com.example.locationreminder.data.ReminderDatabase
import java.util.concurrent.ConcurrentHashMap

open class ReminderAdapter(
    open var listener: ReminderClickListener? = null
) : RecycleView.Adapter<ReminderAdapter.ViewHolder>() {

    companion object {
        fun getListener(adapter: ReminderAdapter): ReminderClickListener? {
            return adapter.listener
        }
    }

    interface ReminderClickListener {
        fun onReminderClick(reminder: Reminder)
    }

    private val reminders = mutableListOf<Reminder>()
    private var database: ReminderDatabase? = null
    private var parentContext: android.content.Context? = null
    
    // Track last trigger time for each reminder ID to prevent spam notifications
    private val lastTriggerTimes = ConcurrentHashMap<Long, Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(reminders[position], position)
    }

    override fun getItemCount(): Int = reminders.size

    fun submitList(list: List<Reminder>, context: android.content.Context) {
        database = ReminderDatabase(context.applicationContext)
        parentContext = context
        
        // Reset trigger times when list changes
        lastTriggerTimes.clear()
        
        reminders.clear()
        reminders.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecycleView.ViewHolder(itemView), View.OnClickListener {
        var itemPosition: Int = 0
        
        private val titleText: TextView = itemView.findViewById(R.id.reminderTitle)
        private val descriptionText: TextView = itemView.findViewById(R.id.reminderDescription)
        private val radiusText: TextView = itemView.findViewById(R.id.reminderRadius)
        private val locationIcon: ImageView = itemView.findViewById(R.id.locationIcon)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        private val triggeredIndicator: View = itemView.findViewById(R.id.triggeredIndicator)
        private val deleteBtn: Button = itemView.findViewById(R.id.reminderDelete)
        private val statusBtn: Button = itemView.findViewById(R.id.reminderStatusBtn)

        fun bind(reminder: Reminder, pos: Int) {
            itemPosition = pos
            titleText.text = reminder.title
            descriptionText.text = reminder.description.ifEmpty { "No description" }
            radiusText.text = "${reminder.proximityRadiusMeters}m radius"

            // Set status indicator color based on active state
            val isActive = reminder.is_active
            statusIndicator.alpha = if (isActive) 1f else 0.5f
            
            // Show/hide toggle button
            statusBtn.visibility = if (!isActive) View.VISIBLE else View.GONE
            
            triggerIndicator(reminder.id, pos)
        }

        private fun triggerIndicator(reminderId: Long, pos: Int) {
            val now = System.currentTimeMillis() / 1000L
            val lastTriggerTime = lastTriggerTimes[reminderId]
            
            // Only show triggered indicator if there was a previous trigger and 30+ seconds have passed
            if (lastTriggerTime != null && (now - lastTriggerTime) >= 30) {
                triggeredIndicator.visibility = View.VISIBLE
                animatePulse(triggeredIndicator)
            } else {
                triggeredIndicator.visibility = View.GONE
            }
        }

        private fun animatePulse(view: View) {
            view.animate()
                .scaleY(1.5f)
                .alpha(0.3f)
                .setDuration(300)
                .withEndAction {
                    // Reset to default
                    triggeredIndicator.alpha = 1f
                    triggeredIndicator.scaleX = 1f
                    triggeredIndicator.scaleY = 1f
                }
        }

        init {
            itemView.setOnClickListener { 
                reminder?.let { listener?.onReminderClick(it) }
            }
            deleteBtn.setOnClickListener { 
                val reminder = itemView.tag as? Reminder
                if (reminder != null) {
                    database?.delete(reminder.id)
                    notifyItemRemoved(itemPosition)
                }
            }
            statusBtn.setOnClickListener {
                val reminder = itemView.tag as? Reminder
                if (reminder != null) {
                    toggleStatus(reminder)
                }
            }
        }

        private var reminder: Reminder? = null
        
        fun setReminder(r: Reminder) {
            this.reminder = r
        }

        private fun toggleStatus(reminder: Reminder) {
            // Toggle the status in local cache - database update happens separately
            val isActive = reminder.is_active
            lastTriggerTimes[reminder.id] = System.currentTimeMillis() / 1000L
            notifyDataSetChanged()
        }

        private fun onReminderClick(reminder: Reminder?) {
            reminder?.let { listener?.onReminderClick(it) }
        }

        override fun onClick(v: View?) {}
    }
}