package com.aman.agent.utils

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraManager - Handles photo capture using CameraX
 * Supports both front and back cameras with quality settings
 */
class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    /**
     * Capture photo with specified camera and quality
     */
    suspend fun capturePhoto(
        cameraType: String = "back",
        quality: Int = 80
    ): File = withContext(Dispatchers.Main) {
        try {
            Log.i(TAG, "Capturing photo with $cameraType camera, quality: $quality")

            // Initialize camera if needed
            initializeCamera(cameraType, quality)

            // Create output file
            val photoFile = createPhotoFile()

            // Capture photo
            capturePhotoToFile(photoFile)

            Log.i(TAG, "Photo captured successfully: ${photoFile.absolutePath}")
            photoFile

        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture photo", e)
            throw e
        }
    }

    /**
     * Initialize camera with specified settings
     */
    private suspend fun initializeCamera(cameraType: String, quality: Int) {
        try {
            // Get camera provider
            cameraProvider = getCameraProvider()

            // Select camera
            val cameraSelector = when (cameraType.lowercase()) {
                "front" -> CameraSelector.DEFAULT_FRONT_CAMERA
                "back" -> CameraSelector.DEFAULT_BACK_CAMERA
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }

            // Create image capture use case
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(quality)
                .build()

            // Unbind all use cases before rebinding
            cameraProvider?.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider?.bindToLifecycle(
                ProcessLifecycleOwner.get(),
                cameraSelector,
                imageCapture
            )

            Log.i(TAG, "Camera initialized: $cameraType")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera", e)
            throw CameraException("Camera initialization failed: ${e.message}")
        }
    }

    /**
     * Get camera provider instance
     */
    private suspend fun getCameraProvider(): ProcessCameraProvider {
        return suspendCancellableCoroutine { continuation ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    continuation.resume(provider)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    /**
     * Create photo file with timestamp
     */
    private fun createPhotoFile(): File {
        val timestamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val filename = "AMAN_$timestamp$PHOTO_EXTENSION"
        
        // Create photos directory if it doesn't exist
        val photosDir = File(context.getExternalFilesDir(null), "photos")
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        
        return File(photosDir, filename)
    }

    /**
     * Capture photo to file
     */
    private suspend fun capturePhotoToFile(photoFile: File): Unit = suspendCancellableCoroutine { continuation ->
        val imageCapture = this.imageCapture
        if (imageCapture == null) {
            continuation.resumeWithException(CameraException("ImageCapture not initialized"))
            return@suspendCancellableCoroutine
        }

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.i(TAG, "Photo saved: ${photoFile.absolutePath}")
                    continuation.resume(Unit)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    continuation.resumeWithException(CameraException("Photo capture failed: ${exception.message}"))
                }
            }
        )
    }

    /**
     * Check if camera is available
     */
    fun isCameraAvailable(cameraType: String): Boolean {
        return try {
            val cameraSelector = when (cameraType.lowercase()) {
                "front" -> CameraSelector.DEFAULT_FRONT_CAMERA
                "back" -> CameraSelector.DEFAULT_BACK_CAMERA
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }
            
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider.hasCamera(cameraSelector)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking camera availability", e)
            false
        }
    }

    /**
     * Get available cameras
     */
    fun getAvailableCameras(): List<String> {
        val cameras = mutableListOf<String>()
        
        try {
            if (isCameraAvailable("back")) {
                cameras.add("back")
            }
            if (isCameraAvailable("front")) {
                cameras.add("front")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available cameras", e)
        }
        
        return cameras
    }

    /**
     * Capture photo in memory (for immediate processing)
     */
    suspend fun capturePhotoInMemory(
        cameraType: String = "back",
        quality: Int = 80
    ): ByteArray = withContext(Dispatchers.Main) {
        try {
            Log.i(TAG, "Capturing photo in memory")

            // Initialize camera if needed
            initializeCamera(cameraType, quality)

            // Capture photo to memory
            capturePhotoToMemory()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture photo in memory", e)
            throw e
        }
    }

    /**
     * Capture photo to memory buffer
     */
    private suspend fun capturePhotoToMemory(): ByteArray = suspendCancellableCoroutine { continuation ->
        val imageCapture = this.imageCapture
        if (imageCapture == null) {
            continuation.resumeWithException(CameraException("ImageCapture not initialized"))
            return@suspendCancellableCoroutine
        }

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        // Convert ImageProxy to ByteArray
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        
                        image.close()
                        continuation.resume(bytes)
                    } catch (e: Exception) {
                        image.close()
                        continuation.resumeWithException(e)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture to memory failed", exception)
                    continuation.resumeWithException(CameraException("Photo capture failed: ${exception.message}"))
                }
            }
        )
    }

    /**
     * Clean up camera resources
     */
    fun cleanup() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageCapture = null
            camera = null
            Log.i(TAG, "Camera resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up camera resources", e)
        }
    }

    /**
     * Get camera info
     */
    fun getCameraInfo(cameraType: String): CameraInfo? {
        return try {
            camera?.cameraInfo
        } catch (e: Exception) {
            Log.e(TAG, "Error getting camera info", e)
            null
        }
    }

    /**
     * Check if flash is available
     */
    fun isFlashAvailable(): Boolean {
        return try {
            camera?.cameraInfo?.hasFlashUnit() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking flash availability", e)
            false
        }
    }

    /**
     * Enable/disable flash
     */
    fun setFlashMode(enabled: Boolean) {
        try {
            imageCapture?.flashMode = if (enabled) {
                ImageCapture.FLASH_MODE_ON
            } else {
                ImageCapture.FLASH_MODE_OFF
            }
            Log.i(TAG, "Flash mode set to: ${if (enabled) "ON" else "OFF"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting flash mode", e)
        }
    }

    /**
     * Custom exception for camera operations
     */
    class CameraException(message: String) : Exception(message)
}
