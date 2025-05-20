package com.example.chinna.ui.practices

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chinna.R
import com.example.chinna.databinding.ItemTaskBinding

class TaskAdapter(
    private val onTaskClick: (TaskItem) -> Unit
) : ListAdapter<TaskItem, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskViewHolder(binding, onTaskClick)
    }
    
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class TaskViewHolder(
        private val binding: ItemTaskBinding,
        private val onTaskClick: (TaskItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(task: TaskItem) {
            binding.tvTaskTitle.text = task.title
            binding.tvTaskDescription.text = task.description
            binding.tvTaskDate.text = task.date
            
            // Set status badge
            val (statusText, statusColor) = when (task.status) {
                TaskStatus.OVERDUE -> "Overdue" to ContextCompat.getColor(binding.root.context, R.color.error)
                TaskStatus.TODAY -> "Today" to ContextCompat.getColor(binding.root.context, R.color.success)
                TaskStatus.UPCOMING -> "In ${task.daysUntil} days" to ContextCompat.getColor(binding.root.context, R.color.warning)
                TaskStatus.FUTURE -> "Week ${task.weekNumber}" to ContextCompat.getColor(binding.root.context, R.color.dark_text_secondary)
            }
            
            binding.tvTaskStatus.text = statusText
            binding.tvTaskStatus.setTextColor(statusColor)
            
            // Show critical icon if needed
            if (task.isCritical) {
                binding.ivCriticalIcon.visibility = android.view.View.VISIBLE
            } else {
                binding.ivCriticalIcon.visibility = android.view.View.GONE
            }
            
            // Set card background based on status
            val cardColor = when (task.status) {
                TaskStatus.OVERDUE -> ContextCompat.getColor(binding.root.context, R.color.error_light)
                TaskStatus.TODAY -> ContextCompat.getColor(binding.root.context, R.color.success_light)
                TaskStatus.UPCOMING -> ContextCompat.getColor(binding.root.context, R.color.warning_light)
                TaskStatus.FUTURE -> ContextCompat.getColor(binding.root.context, R.color.dark_surface)
            }
            
            binding.root.setCardBackgroundColor(cardColor)
            
            binding.root.setOnClickListener {
                onTaskClick(task)
            }
        }
    }
    
    class TaskDiffCallback : DiffUtil.ItemCallback<TaskItem>() {
        override fun areItemsTheSame(oldItem: TaskItem, newItem: TaskItem): Boolean {
            return oldItem.weekNumber == newItem.weekNumber
        }
        
        override fun areContentsTheSame(oldItem: TaskItem, newItem: TaskItem): Boolean {
            return oldItem == newItem
        }
    }
}