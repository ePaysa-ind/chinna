package com.example.chinna.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.chinna.R
import com.example.chinna.data.local.PrefsManager
import com.example.chinna.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chinna.databinding.DialogHistoryBinding
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.example.chinna.model.Crop
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.chinna.data.repository.UserRepository

@AndroidEntryPoint
class HomeFragment : Fragment() {
    
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var prefsManager: PrefsManager
    
    @Inject
    lateinit var userRepository: UserRepository
    
    private var currentCrop: Crop? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupClickListeners()
        setupMenu()
    }
    
    override fun onResume() {
        super.onResume()
        // Reload crop information in case user selected a new crop
        loadCurrentCrop()
    }
    
    private fun setupMenu() {
        // Menu removed - history is now in bottom navigation
    }
    
    private fun setupUI() {
        // Get current user from database
        lifecycleScope.launch {
            val currentUser = userRepository.getCurrentUserSync()
            
            val displayName = currentUser?.name ?: getString(R.string.default_user_name)
            
            requireActivity().runOnUiThread {
                binding.welcomeText.text = getString(R.string.welcome_user, displayName)
            }
            
            // Update current crop information from user data
            currentUser?.let { user ->
                requireActivity().runOnUiThread {
                    if (user.crop.isNotEmpty()) {
                        currentCrop = Crop(
                            id = user.crop.lowercase(),
                            name = user.crop,
                            localName = user.crop,
                            iconRes = getIconForCrop(user.crop)
                        )
                        updateCropDisplay()
                    }
                }
            }
        }
        
        // Load current crop information (from preferences if needed)
        loadCurrentCrop()
    }
    
    private fun loadCurrentCrop() {
        // Check if user has selected a crop through the package of practices
        val prefs = requireContext().getSharedPreferences("selected_crop", android.content.Context.MODE_PRIVATE)
        val cropId = prefs.getString("crop_id", "") ?: ""
        val cropName = prefs.getString("crop_name", "") ?: ""
        
        if (cropId.isNotEmpty() && cropName.isNotEmpty()) {
            // Load crop data from crops_data.json
            try {
                val json = requireContext().assets.open("crops_data.json")
                    .bufferedReader()
                    .use { it.readText() }
                    
                val jsonObject = Gson().fromJson(json, JsonObject::class.java)
                val cropsArray = jsonObject.getAsJsonArray("crops")
                
                cropsArray.forEach { element ->
                    val cropObj = element.asJsonObject
                    if (cropObj.get("id").asString == cropId) {
                        currentCrop = Crop(
                            id = cropObj.get("id").asString,
                            name = cropObj.get("name").asString,
                            localName = cropObj.get("localName").asString,
                            iconRes = getIconForCrop(cropObj.get("id").asString),
                            specificIconRes = getIconForCrop(cropObj.get("id").asString),
                            description = cropObj.get("description").asString
                        )
                        updateCropDisplay()
                        return@forEach
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // No crop selected - just hide the crop indicators
            binding.pestCropContainer.visibility = View.GONE
            binding.advisoryCropContainer.visibility = View.GONE
        }
    }
    
    
    private fun updateCropDisplay() {
        currentCrop?.let { crop ->
            // Update pest identification card
            binding.pestCropContainer.visibility = View.VISIBLE
            binding.pestCropIcon.setImageResource(crop.iconRes)
            binding.pestCropName.text = crop.name
            
            // Update smart advisory card
            binding.advisoryCropContainer.visibility = View.VISIBLE
            binding.advisoryCropIcon.setImageResource(crop.iconRes)
            binding.advisoryCropName.text = crop.name
        }
    }
    
    private fun setupClickListeners() {
        binding.cardPestIdentification.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_camera)
        }
        
        binding.cardExploreCrops.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_practices)
        }
        
        binding.cardSmartAdvisory.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_smart_advisory)
        }
    }
    
    fun showHistoryDialog() {
        val dialogBinding = DialogHistoryBinding.inflate(layoutInflater)
        
        // Get only valid plant results (not "Not a valid plant")
        val validResults = prefsManager.getResults()
            .filter { !it.pestName.equals("Not a valid plant", ignoreCase = true) }
            .take(5)
        
        if (validResults.isEmpty()) {
            dialogBinding.rvHistory.visibility = View.GONE
            dialogBinding.emptyState.visibility = View.VISIBLE
            dialogBinding.tableHeaders.visibility = View.GONE
        } else {
            dialogBinding.rvHistory.visibility = View.VISIBLE
            dialogBinding.emptyState.visibility = View.GONE
            dialogBinding.tableHeaders.visibility = View.VISIBLE
            
            // Set up RecyclerView
            val adapter = HistoryAdapter(validResults)
            dialogBinding.rvHistory.apply {
                layoutManager = LinearLayoutManager(requireContext())
                this.adapter = adapter
            }
        }
        
        // Create dialog with consistent dark theme
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Chinna_Dialog)
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()
            
        // Show dialog with proper styling
        dialog.show()
        
        // Ensure button has consistent styling
        val positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
        positiveButton.setTextColor(requireContext().getColor(R.color.dark_accent))
        positiveButton.textSize = 16f
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun getIconForCrop(cropName: String): Int {
        return when (cropName.lowercase()) {
            "okra" -> R.drawable.ic_crop_okra
            "chilli", "chillies" -> R.drawable.ic_crop_chilli
            "tomato", "tomatoes" -> R.drawable.ic_crop_tomato
            "rice" -> R.drawable.ic_crop_rice
            "wheat" -> R.drawable.ic_crop_wheat
            "cotton" -> R.drawable.ic_cotton
            "maize" -> R.drawable.ic_maize
            "soybean" -> R.drawable.ic_soybean
            else -> R.drawable.ic_crop
        }
    }
}