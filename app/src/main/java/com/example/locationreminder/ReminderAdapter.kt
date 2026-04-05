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

open class ReminderAdapter(
    open var listener: ReminderClickListener? = null,
    var database: ReminderDatabase? = null,
    var onDeleteCallback: (() -> Unit)? = null
) : RecycleView.Adapter<ReminderAdapter.ViewHolder>() {

    interface ReminderClickListener {
        fun onReminderClick(reminder: Reminder)
    }

    private val reminders = mutableListOf<Reminder>()
    private var parentContext: android.content.Context? = null
    
    // Track last trigger time for each reminder ID to prevent spam notifications
    private val lastTriggerTimes = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.item_reminder, parent, false)
        return ViewHolder(view, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(reminders[position], position)
    }

    override fun getItemCount(): Int = reminders.size

    fun submitList(list: List<Reminder>, context: android.content.Context) {
        parentContext = context
        
        // Reset trigger times when list changes
        lastTriggerTimes.clear()
        
        reminders.clear()
        reminders.addAll(list)
        notifyDataSetChanged()
    }

    fun onItemClicked(reminder: Reminder) {
        listener?.onReminderClick(reminder)
    }

    fun toggleStatus(reminder: Reminder) {
        val isActive = reminder.is_active
        lastTriggerTimes[reminder.id] = System.currentTimeMillis() / 1000L
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View, adapterInstance: ReminderAdapter) : 
            RecycleView.ViewHolder(itemView), View.OnClickListener {
        
        var itemPosition: Int = 0
        
        private val titleText: TextView = itemView.findViewById(R.id.reminderTitle)
        private val descriptionText: TextView = itemView.findViewById(R.id.reminderDescription)
        private val radiusText: TextView = itemView.findViewById(R.id.reminderRadius)
        private val locationIcon: ImageView = itemView.findViewById(R.id.locationIcon)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        private val triggeredIndicator: View = itemView.findViewById(R.id.triggeredIndicator)
        private val deleteBtn: Button = itemView.findViewById(R.id.reminderDelete)
        private val statusBtn: Button = itemView.findViewById(R.id.reminderStatusBtn)

        private var reminder: Reminder? = null
        
        fun bind(reminder: Reminder, pos: Int) {
            itemPosition = pos
            this.reminder = reminder
            
            // Set the view tag for easy access from click listeners
            itemView.tag = reminder
            
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
            adapterInstance?.let { adapter ->
                itemView.setOnClickListener { 
                    val currentReminder = (it.tag as? Reminder) ?: return@setOnClickListener
                    adapter.onItemClicked(currentReminder)
                }
                
                deleteBtn.setOnClickListener { 
                    val currentReminder = (itemView.tag as? Reminder)
                    if (currentReminder != null) {
                        adapter.database?.delete(currentReminder.id)
                    adapter.onDeleteCallback?.invoke()
                    }
                }
                
                statusBtn.setOnClickListener {
                    val currentReminder = (itemView.tag as? Reminder)
                    if (currentReminder != null) {
                        adapter.toggleStatus(currentReminder)
                    }
                }
            }
        }

        override fun onClick(p0: View?) {}
    }
}