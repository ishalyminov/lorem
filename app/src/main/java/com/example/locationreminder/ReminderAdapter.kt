package com.example.locationreminder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView as RecycleView
import com.example.locationreminder.data.Reminder

class ReminderAdapter(
    private val listener: ReminderClickListener?
) : RecycleView.Adapter<ReminderAdapter.ViewHolder>() {

    interface ReminderClickListener {
        fun onReminderClick(reminder: Reminder)
    }

    private val reminders = mutableListOf<Reminder>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(reminders[position])
    }

    override fun getItemCount(): Int = reminders.size

    fun submitList(list: List<Reminder>) {
        reminders.clear()
        reminders.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecycleView.ViewHolder(itemView), View.OnClickListener {
        private val titleText: TextView = itemView.findViewById(R.id.reminderTitle)
        private val descriptionText: TextView = itemView.findViewById(R.id.reminderDescription)
        private val radiusText: TextView = itemView.findViewById(R.id.reminderRadius)

        fun bind(reminder: Reminder) {
            titleText.text = reminder.title
            descriptionText.text = reminder.description.ifEmpty { "No description" }
            radiusText.text = "${reminder.proximityRadiusMeters}m radius"
            itemView.setOnClickListener { onReminderClick(reminder) }
        }

        private fun onReminderClick(reminder: Reminder) {
            listener?.onReminderClick(reminder)
        }

        override fun onClick(v: View?) {}
    }
}