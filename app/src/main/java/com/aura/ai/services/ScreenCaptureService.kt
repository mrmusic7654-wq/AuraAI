package com.aura.ai.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import com.aura.ai.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class ScreenCaptureService : Service() {
    
    @Inject
    lateinit var screenStateManager: ScreenStateManager
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var captureJob: Job? = null
    
    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "screen_capture"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        
        var mediaProjectionIntent: Intent? = null
        var resultCode: Int = 0
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        backgroundThread = HandlerThread("ScreenCapture").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        
        if (resultCode != -1 && data != null) {
            startCapture(resultCode, data)
        }
        
        return START_STICKY
    }
    
    private fun startCapture(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )
        
        startPeriodicCapture()
    }
    
    private fun startPeriodicCapture() {
        captureJob = serviceScope.launch {
            while (isActive) {
                captureScreen()
                delay(500) // Capture every 500ms
            }
        }
    }
    
    private suspend fun captureScreen(): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val image = imageReader?.acquireLatestImage() ?: return@withContext null
                
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width
                
                val bitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()
                
                // Crop to actual size
                Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            } catch (e: Exception) {
                Timber.e(e, "Failed to capture screen")
                null
            }
        }
    }
    
    suspend fun takeScreenshot(): Bitmap? {
        return captureScreen()
    }
    
    suspend fun saveScreenshot(bitmap: Bitmap, filename: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val screenshotsDir = File(cacheDir, "screenshots")
                if (!screenshotsDir.exists()) {
                    screenshotsDir.mkdirs()
                }
                
                val file = File(screenshotsDir, "$filename.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                file
            } catch (e: Exception) {
                Timber.e(e, "Failed to save screenshot")
                null
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Aura AI Screen Capture")
        .setContentText("Capturing screen for AI analysis")
        .setSmallIcon(R.drawable.ic_aura_logo)
        .setOngoing(true)
        .build()
    
    override fun onDestroy() {
        captureJob?.cancel()
        serviceScope.cancel()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        backgroundThread?.quitSafely()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
