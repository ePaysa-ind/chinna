package com.example.chinna.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.chinna.databinding.ItemHistoryBinding
import com.example.chinna.model.PestResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val results: List<PestResult>
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(results[position], position + 1)
    }

    override fun getItemCount() = results.size

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: PestResult, serialNumber: Int) {
            // Set serial number
            binding.tvSerial.text = serialNumber.toString()
            
            // Format date
            val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            binding.tvDate.text = dateFormat.format(Date(result.timestamp))
            
            // Use plant name if available, otherwise extract from summary
            val cropName = if (!result.plantName.isNullOrEmpty() && result.plantName != "Unknown") {
                result.plantName
            } else {
                extractCropName(result)
            }
            binding.tvCrop.text = cropName
            
            // Show pest/disease name or "No pest/disease" for healthy plants
            val issue = when {
                result.pestName.equals("Healthy", ignoreCase = true) -> "No pest/disease"
                result.pestName.contains(" found", ignoreCase = true) -> result.pestName.replace(" found", "", ignoreCase = true)
                else -> result.pestName
            }
            binding.tvIssue.text = issue
            
            // Show confidence
            binding.tvConfidence.text = "${result.confidence.toInt()}%"
        }
        
        private fun extractCropName(result: PestResult): String {
            // Extract the English name without scientific name
            val plantName = result.plantName?.split("(")?.firstOrNull()?.trim() ?: ""
            
            // If plant name is available, use it
            if (plantName.isNotEmpty() && plantName != "Unknown") {
                return plantName
            }
            
            // Otherwise try to extract from summary
            val summary = result.summary.lowercase()
            return when {
                summary.contains("tomato") -> "Tomato"
                summary.contains("chilli") || summary.contains("chili") -> "Chilli"
                summary.contains("okra") -> "Okra"
                summary.contains("cotton") -> "Cotton"
                summary.contains("rice") -> "Rice"
                summary.contains("wheat") -> "Wheat"
                summary.contains("maize") || summary.contains("corn") -> "Maize"
                summary.contains("soybean") -> "Soybean"
                else -> "Plant"
            }
        }
    }
}