package com.example.chinna.ui.practices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chinna.databinding.ItemCropBinding
import com.example.chinna.model.Crop
import com.bumptech.glide.Glide

class CropAdapter(
    private val onItemClick: (Crop) -> Unit
) : ListAdapter<Crop, CropAdapter.CropViewHolder>(CropDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CropViewHolder {
        val binding = ItemCropBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CropViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CropViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CropViewHolder(
        private val binding: ItemCropBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(crop: Crop) {
            binding.cropName.text = crop.name
            // Don't show local name
            binding.cropLocalName.text = ""
            
            // Load image from assets
            val context = binding.root.context
            val assetFileName = getAssetFileName(crop.name)
            
            if (assetFileName != null) {
                Glide.with(context)
                    .load("file:///android_asset/$assetFileName")
                    .into(binding.cropIcon)
            } else {
                binding.cropIcon.setImageResource(crop.iconRes)
            }
        }
        
        private fun getAssetFileName(cropName: String): String? {
            return when (cropName.lowercase()) {
                "okra" -> "okra.png"
                "chilli" -> "chillies.png"
                "tomato" -> "tomato.png"
                "cotton" -> "cotton.png"
                "maize" -> "corn.png"
                "soybean" -> "soybean.png"
                "rice" -> "rice.png"
                "wheat" -> "wheat.png"
                else -> null
            }
        }
    }

    class CropDiffCallback : DiffUtil.ItemCallback<Crop>() {
        override fun areItemsTheSame(oldItem: Crop, newItem: Crop): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Crop, newItem: Crop): Boolean {
            return oldItem == newItem
        }
    }
}