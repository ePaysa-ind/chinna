package com.example.chinna.ui.smart_advisory

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.chinna.R
import com.example.chinna.databinding.ItemNudgeCardBinding

class NudgeCardAdapter(
    private val nudgeCards: List<NudgeCard>,
    private val onCardClick: (NudgeCard) -> Unit,
    private val onAskAdvice: (NudgeCard) -> Unit
) : RecyclerView.Adapter<NudgeCardAdapter.NudgeCardViewHolder>() {

    inner class NudgeCardViewHolder(private val binding: ItemNudgeCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(nudgeCard: NudgeCard) {
            binding.apply {
                titleTextView.text = nudgeCard.title
                shortDescriptionTextView.text = nudgeCard.shortDescription
                fullDescriptionTextView.text = nudgeCard.fullDescription

                // Set priority badge with better context
                val priorityLabelMap = mapOf(
                    "urgent" to "URGENT - DO NOW",
                    "high" to "HIGH PRIORITY", 
                    "medium" to "MEDIUM PRIORITY",
                    "low" to "LOW PRIORITY"
                )
                
                priorityBadge.text = priorityLabelMap[nudgeCard.priority.lowercase()] 
                    ?: nudgeCard.priority.uppercase()
                    
                val color = when (nudgeCard.priority.lowercase()) {
                    "urgent" -> R.color.error
                    "high" -> R.color.warning
                    "medium" -> R.color.primary
                    else -> R.color.text_secondary
                }
                priorityBadge.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, color)
                )

                // Handle expansion
                expandableContent.visibility = if (nudgeCard.isExpanded) View.VISIBLE else View.GONE

                // Click listeners
                root.setOnClickListener {
                    onCardClick(nudgeCard)
                }

                askAdviceButton.setOnClickListener {
                    onAskAdvice(nudgeCard)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NudgeCardViewHolder {
        val binding = ItemNudgeCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NudgeCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NudgeCardViewHolder, position: Int) {
        holder.bind(nudgeCards[position])
    }

    override fun getItemCount() = nudgeCards.size
}