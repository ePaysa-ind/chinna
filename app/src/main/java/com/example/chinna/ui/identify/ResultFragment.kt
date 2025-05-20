package com.example.chinna.ui.identify

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.chinna.R
import com.example.chinna.data.local.PrefsManager
import com.example.chinna.data.remote.GeminiService
import com.example.chinna.databinding.FragmentResultBinding
import com.example.chinna.model.PestResult
import com.example.chinna.model.Severity
import com.example.chinna.util.NetworkMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import android.util.Log

@AndroidEntryPoint
class ResultFragment : Fragment() {
    
    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!
    
    @Inject lateinit var geminiService: GeminiService
    @Inject lateinit var networkMonitor: NetworkMonitor
    @Inject lateinit var prefsManager: PrefsManager
    
    private val args: ResultFragmentArgs by navArgs()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        analyzeCropImage()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun setupUI() {
        // Load and display the captured image
        val imageFile = File(args.imagePath)
        if (imageFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                if (bitmap != null) {
                    binding.capturedImage.setImageBitmap(bitmap)
                } else {
                    Log.e("ResultFragment", "Failed to decode bitmap from ${args.imagePath}")
                    showError("Failed to load image")
                }
            } catch (e: Exception) {
                Log.e("ResultFragment", "Error loading image", e)
                showError("Error loading image: ${e.message}")
            }
        } else {
            Log.e("ResultFragment", "Image file does not exist: ${args.imagePath}")
            showError("Image file not found")
        }
        
        // Set up click listeners
        setupClickListeners()
    }
    
    private fun analyzeCropImage() {
        // Show loading state
        binding.progressBar.visibility = View.VISIBLE
        binding.resultContent.visibility = View.GONE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val imageFile = File(args.imagePath)
                if (!imageFile.exists()) {
                    showError("Image file not found")
                    return@launch
                }
                
                // Load bitmap in background
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(imageFile.absolutePath)
                }
                
                if (bitmap == null) {
                    showError("Failed to load image")
                    return@launch
                }
                
                val isConnected = networkMonitor.isNetworkAvailable()
                
                if (!isConnected) {
                    prefsManager.addToOfflineQueue(args.imagePath)
                    showOfflineMessage()
                } else {
                    // Perform Gemini analysis
                    analyzeWithGemini(bitmap)
                }
            } catch (e: Exception) {
                showError("Analysis failed: ${e.message}")
            }
        }
    }
    
    data class DisplayResult(
        val pestName: String,
        val confidence: String,
        val plantName: String,
        val possiblePlants: String,
        val severity: Severity,
        val summary: String,
        val treatment: String,
        val prevention: String
    )
    
    private fun convertToDisplayResult(result: GeminiService.AnalysisResult): DisplayResult {
        val severity = when (result.severity.uppercase()) {
            "HIGH" -> Severity.HIGH
            "MEDIUM" -> Severity.MEDIUM
            else -> Severity.LOW
        }
        
        return DisplayResult(
            pestName = result.pestName,
            confidence = result.confidence,
            plantName = result.plantName,
            possiblePlants = result.possiblePlants,
            severity = severity,
            summary = result.summary,
            treatment = result.treatment,
            prevention = result.prevention
        )
    }
    
    private fun displayResult(result: DisplayResult) {
        binding.progressBar.visibility = View.GONE
        binding.resultContent.visibility = View.VISIBLE
        
        // Check if this is a valid plant image
        if (result.pestName.equals("Not a valid plant", ignoreCase = true)) {
            binding.pestName.text = "Invalid Image"
            binding.confidenceText.text = "100% confidence in assessment"
            binding.summary.text = result.summary
            binding.treatment.text = "Not applicable"
            binding.prevention.text = "Not applicable"
            
            // Hide severity indicator for invalid images
            binding.severityIndicator.visibility = View.GONE
            binding.severityText.visibility = View.GONE
            
            // Show data notice with different message
            binding.dataNotice.visibility = View.VISIBLE
            binding.dataNotice.text = "⚠️ Image saved on your phone. Delete if not needed to save storage."
            return
        }
        
        // Display regular results
        binding.pestName.text = result.pestName
        binding.confidenceText.text = "${result.confidence} confidence in assessment"
        
        // Color confidence icon based on confidence level
        val confidenceValue = result.confidence.replace("%", "").toFloatOrNull() ?: 0f
        val confidenceColor = when {
            confidenceValue >= 80 -> R.color.success  // Green for high confidence
            confidenceValue >= 60 -> R.color.warning  // Yellow for medium confidence
            else -> R.color.danger                    // Red for low confidence
        }
        binding.confidenceIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), confidenceColor)
        
        // Enhanced summary with plant name
        val summaryText = when {
            result.plantName != "Unknown" -> result.summary
            result.possiblePlants.isNotEmpty() && result.possiblePlants != "Not applicable" -> {
                "This plant (possibly ${result.possiblePlants}) has ${result.summary.lowercase()}"
            }
            else -> result.summary
        }
        binding.summary.text = summaryText
        
        binding.treatment.text = result.treatment
        
        // Display prevention with bullet points
        val preventionText = result.prevention
            .split(". ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n") { line ->
                if (line.endsWith(".")) {
                    "• $line"
                } else {
                    "• $line."
                }
            }
        binding.prevention.text = preventionText
        
        // Set severity indicator (severity indicates risk level)
        val (severityColor, text) = when (result.severity) {
            Severity.HIGH -> R.color.danger to "High Risk (Severe infestation)"
            Severity.MEDIUM -> R.color.warning to "Medium Risk (Moderate infestation)"
            Severity.LOW -> R.color.success to "Low Risk (Minor infestation)"
        }
        
        binding.severityIndicator.setBackgroundColor(
            ContextCompat.getColor(requireContext(), severityColor)
        )
        binding.severityText.text = text
        binding.severityText.setTextColor(
            ContextCompat.getColor(requireContext(), severityColor)
        )
        
        // Show data storage notice
        binding.dataNotice.visibility = View.VISIBLE
        binding.dataNotice.text = "ℹ️ This image is stored locally on your phone. Remember to delete old images to save storage."
        
        // Save the result
        saveResult(result)
    }
    
    private fun setupClickListeners() {
        binding.btnViewDetails.setOnClickListener {
            toggleDetails()
        }
        
        binding.btnRetake.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnDone.setOnClickListener {
            findNavController().navigate(
                ResultFragmentDirections.actionResultToHome()
            )
        }
    }
    
    private fun analyzeWithGemini(bitmap: Bitmap) {
        binding.progressBar.visibility = View.VISIBLE
        binding.resultContent.visibility = View.GONE
        
        Log.d("ResultFragment", "Starting Gemini analysis")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("ResultFragment", "Calling Gemini API")
                val result = geminiService.analyzeCropImage(bitmap)
                Log.d("ResultFragment", "Gemini API returned: $result")
                
                withContext(Dispatchers.Main) {
                    val displayResult = convertToDisplayResult(result)
                    displayResult(displayResult)
                }
            } catch (e: Exception) {
                Log.e("ResultFragment", "Gemini analysis failed", e)
                withContext(Dispatchers.Main) {
                    showError("Analysis failed: ${e.message}")
                }
            }
        }
    }
    
    private fun toggleDetails() {
        if (binding.detailsSection.visibility == View.GONE) {
            binding.detailsSection.visibility = View.VISIBLE
            binding.btnViewDetails.text = "Hide Details"
        } else {
            binding.detailsSection.visibility = View.GONE
            binding.btnViewDetails.text = "View Details"
        }
    }
    
    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    
    private fun saveResult(result: DisplayResult) {
        // Don't save invalid plant results
        if (result.pestName.equals("Not a valid plant", ignoreCase = true)) {
            return
        }
        
        // Parse confidence percentage to float
        val confidenceValue = result.confidence.replace("%", "").toFloatOrNull() ?: 0f
        
        val pestResult = PestResult(
            id = UUID.randomUUID().toString(),
            userId = prefsManager.getUser()?.userId ?: "",
            imagePath = args.imagePath,
            pestName = result.pestName,
            severity = result.severity,
            summary = result.summary,
            treatment = result.treatment,
            prevention = result.prevention,
            confidence = confidenceValue,
            timestamp = System.currentTimeMillis(),
            plantName = result.plantName
        )
        
        prefsManager.addResult(pestResult)
    }
    
    private fun showOfflineMessage() {
        binding.progressBar.visibility = View.GONE
        binding.resultContent.visibility = View.VISIBLE
        
        // Show offline message
        binding.pestName.text = "Analysis Pending"
        binding.confidenceText.text = "Waiting for internet connection"
        binding.summary.text = "Your image has been saved and will be analyzed when you're back online."
        binding.treatment.text = "Connect to internet to get treatment advice"
        binding.prevention.text = "Connect to internet to get prevention advice"
        
        // Set warning color for offline state
        binding.severityIndicator.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.warning)
        )
        binding.severityText.text = "Offline - Analysis Pending"
        binding.severityText.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.warning)
        )
    }
}