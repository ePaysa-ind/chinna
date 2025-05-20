package com.example.chinna.ui.identify

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.chinna.databinding.FragmentCameraBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory

@AndroidEntryPoint
class CameraFragment : Fragment() {
    
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var isNavigatingToResult = false
    private var isCameraStarting = false
    private var cameraRetryCount = 0
    private val maxRetries = 3
    
    private lateinit var orientationEventListener: OrientationEventListener
    
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showError(
                "Camera Permission Required",
                "To take photos of crops, please grant camera permission in settings. You can still use the gallery option."
            )
            binding.btnRetry.text = "Open Settings"
            binding.btnRetry.setOnClickListener {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
        }
    }
    
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            binding.progressBar.visibility = View.VISIBLE
            Log.d("CameraFragment", "Gallery image selected: $uri")
            
            try {
                // Create a local copy of the selected image
                val filename = "gallery_${System.currentTimeMillis()}.jpg"
                val file = File(requireContext().cacheDir, filename)
                
                // Copy image and resize if needed
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) {
                        // Resize if image is too large
                        val maxSize = 1920
                        val scale = if (bitmap.width > bitmap.height) {
                            maxSize.toFloat() / bitmap.width
                        } else {
                            maxSize.toFloat() / bitmap.height
                        }
                        
                        val scaledBitmap = if (scale < 1) {
                            Bitmap.createScaledBitmap(
                                bitmap,
                                (bitmap.width * scale).toInt(),
                                (bitmap.height * scale).toInt(),
                                true
                            )
                        } else {
                            bitmap
                        }
                        
                        // Save to file
                        file.outputStream().use { output ->
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                        }
                        
                        // Clean up
                        if (scaledBitmap != bitmap) {
                            scaledBitmap.recycle()
                        }
                        bitmap.recycle()
                    } else {
                        throw Exception("Failed to decode image")
                    }
                }
                
                Log.d("CameraFragment", "Gallery image processed to: ${file.absolutePath}")
                binding.progressBar.visibility = View.GONE
                
                // Navigate to result with the local file path
                isNavigatingToResult = true
                val action = CameraFragmentDirections
                    .actionCameraToResult(file.absolutePath)
                findNavController().navigate(action)
            } catch (e: Exception) {
                Log.e("CameraFragment", "Failed to process gallery image", e)
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    context,
                    "Failed to process image: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Handle edge-to-edge display for Android 15+
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        isNavigatingToResult = false
        isCameraStarting = false
        
        setupClickListeners()
        
        // Delay camera permission check to ensure view is ready
        binding.root.postDelayed({
            if (_binding != null) {
                checkCameraPermission()
            }
        }, 300)
    }
    
    override fun onPause() {
        super.onPause()
        // Properly unbind camera to free resources
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            // Ignore errors during cleanup
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Reset flags
        isNavigatingToResult = false
        isCameraStarting = false
        
        // Only restart camera if we have permission and executor is ready
        if (::cameraExecutor.isInitialized && !cameraExecutor.isShutdown &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED && _binding != null) {
            // Add delay to prevent rapid camera restarts
            binding.root.postDelayed({
                if (_binding != null && !isCameraStarting) {
                    startCamera()
                }
            }, 500)
        }
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startCamera() {
        // Don't start camera if already starting or binding is null
        if (isCameraStarting || _binding == null) return
        
        isCameraStarting = true
        
        // Show loading state
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCapture.isEnabled = false
        binding.btnGallery.isEnabled = false
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            try {
                // Check binding again in case fragment was destroyed while waiting
                if (_binding == null) {
                    isCameraStarting = false
                    return@addListener
                }
                
                cameraProvider = cameraProviderFuture.get()
                
                // Build preview
                val preview = Preview.Builder()
                    .setTargetResolution(android.util.Size(1920, 1080))
                    .build()
                
                // ImageCapture
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetResolution(android.util.Size(1920, 1080))
                    .setJpegQuality(85)
                    .build()
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                try {
                    // Unbind all use cases before rebinding
                    cameraProvider?.unbindAll()
                    
                    // Bind use cases to camera
                    cameraProvider?.bindToLifecycle(
                        viewLifecycleOwner, cameraSelector, preview, imageCapture
                    )
                    
                    // Set surface provider after binding
                    preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                    
                    // Camera started successfully
                    binding.progressBar.visibility = View.GONE
                    binding.btnCapture.isEnabled = true
                    binding.btnGallery.isEnabled = true
                    hideError()
                    Log.d("CameraFragment", "Camera started successfully")
                } catch (exc: Exception) {
                    Log.e("CameraFragment", "Camera binding failed", exc)
                    binding.progressBar.visibility = View.GONE
                    handleCameraError("Camera binding failed", exc)
                }
            } catch (exc: Exception) {
                Log.e("CameraFragment", "Failed to start camera", exc)
                binding.progressBar.visibility = View.GONE
                handleCameraError("Failed to start camera", exc)
            } finally {
                isCameraStarting = false
            }
            
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    
    private fun setupClickListeners() {
        binding.btnCapture.setOnClickListener {
            takePhoto()
        }
        
        binding.btnGallery.setOnClickListener {
            pickImageFromGallery()
        }
        
        binding.btnClose.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnRetry.setOnClickListener {
            hideError()
            startCamera()
        }
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCapture.isEnabled = false
        
        val photoFile = File(
            requireContext().cacheDir,
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        binding.progressBar.visibility = View.GONE
                        binding.btnCapture.isEnabled = true
                        
                        // Verify file exists
                        if (photoFile.exists()) {
                            isNavigatingToResult = true
                            Log.d("CameraFragment", "Photo saved: ${photoFile.absolutePath}")
                            val action = CameraFragmentDirections
                                .actionCameraToResult(photoFile.absolutePath)
                            findNavController().navigate(action)
                        } else {
                            Log.e("CameraFragment", "Photo file not found after capture")
                            Toast.makeText(
                                context,
                                "Failed to save photo",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e("CameraFragment", "Error navigating to result", e)
                        binding.progressBar.visibility = View.GONE
                        binding.btnCapture.isEnabled = true
                        Toast.makeText(
                            context,
                            "Error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
                override fun onError(exception: ImageCaptureException) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCapture.isEnabled = true
                    Log.e("CameraFragment", "Photo capture error", exception)
                    val errorMessage = when (exception.imageCaptureError) {
                        ImageCapture.ERROR_UNKNOWN -> "Unknown error occurred"
                        ImageCapture.ERROR_FILE_IO -> "Failed to save photo"
                        ImageCapture.ERROR_CAPTURE_FAILED -> "Photo capture failed"
                        ImageCapture.ERROR_CAMERA_CLOSED -> "Camera was closed"
                        ImageCapture.ERROR_INVALID_CAMERA -> "Invalid camera"
                        else -> "Photo capture failed: ${exception.message}"
                    }
                    Toast.makeText(
                        context,
                        errorMessage,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
    
    override fun onStart() {
        super.onStart()
        orientationEventListener = object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                // Update image capture rotation based on device orientation
                imageCapture?.targetRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
    }
    
    override fun onStop() {
        super.onStop()
        if (::orientationEventListener.isInitialized) {
            orientationEventListener.disable()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up camera resources properly
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
        } catch (e: Exception) {
            // Ignore errors during cleanup
        }
        
        // Clear camera use cases
        imageCapture = null
        
        // Shutdown executor only if it's initialized
        if (::cameraExecutor.isInitialized) {
            try {
                cameraExecutor.shutdown()
                if (!cameraExecutor.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    cameraExecutor.shutdownNow()
                }
            } catch (e: Exception) {
                cameraExecutor.shutdownNow()
            }
        }
        
        _binding = null
    }
    
    private fun pickImageFromGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    
    private fun showError(title: String, detail: String) {
        if (_binding == null) return
        
        binding.errorContainer.visibility = View.VISIBLE
        binding.viewFinder.visibility = View.GONE
        binding.errorMessage.text = title
        binding.errorDetail.text = detail
        
        // Hide camera controls
        binding.btnCapture.isEnabled = false
        binding.btnGallery.isEnabled = true  // Gallery should still work
    }
    
    private fun hideError() {
        if (_binding == null) return
        
        binding.errorContainer.visibility = View.GONE
        binding.viewFinder.visibility = View.VISIBLE
        
        // Re-enable camera controls
        binding.btnCapture.isEnabled = true
        cameraRetryCount = 0
    }
    
    private fun handleCameraError(message: String, exception: Exception) {
        cameraRetryCount++
        
        val errorDetail = when {
            exception.message?.contains("CAMERA_IN_USE") == true -> 
                "Camera is being used by another app. Please close other camera apps and try again."
            exception.message?.contains("CAMERA_DISCONNECTED") == true -> 
                "Camera disconnected. Please restart the app."
            exception.message?.contains("MAX_CAMERAS_IN_USE") == true -> 
                "Too many cameras in use. Please close other apps."
            exception.message?.contains("CAMERA_DISABLED") == true -> 
                "Camera is disabled. Please check device settings."
            cameraRetryCount >= maxRetries -> 
                "Failed after $maxRetries attempts. Your device camera may be experiencing issues."
            else -> 
                "Tap 'Retry Camera' or use the gallery to select an image."
        }
        
        showError(message, errorDetail)
        
        // Log the error for debugging
        android.util.Log.e("CameraFragment", "$message: ${exception.message}", exception)
        
        // Try to clean up
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        
        // Disable retry button if max retries reached
        if (cameraRetryCount >= maxRetries) {
            binding.btnRetry.text = "Camera Unavailable"
            binding.btnRetry.isEnabled = false
        }
    }
}