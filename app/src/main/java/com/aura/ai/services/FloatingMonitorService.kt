package com.aura.ai.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import com.aura.ai.MainActivity
import com.aura.ai.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FloatingMonitorService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
    private var headerIcon: ImageView? = null
    private var taskTitle: TextView? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null
    private var sectionsContainer: LinearLayout? = null
    private var agentText: TextView? = null
    private var pauseIcon: ImageView? = null
    private var stopIcon: ImageView? = null

    companion object {
        var instance: FloatingMonitorService? = null
        var isShowing: Boolean = false
        private val sectionStatuses = mutableMapOf<String, String>()
        private var currentComplete = 0
        private var currentTotal = 0
        private var currentAgent = "Aura AI"
        private var currentTitle = "Idle"
        private var currentSection = 0
        private var totalSections = 0

        fun show(context: Context) {
            if (!isShowing) context.startService(Intent(context, FloatingMonitorService::class.java))
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, FloatingMonitorService::class.java)); isShowing = false
        }

        fun updateTask(title: String, completed: Int, total: Int, section: Int = 0, totalSec: Int = 0) {
            currentTitle = title; currentComplete = completed; currentTotal = total; currentSection = section; totalSections = totalSec
            instance?.refreshUI()
        }

        fun updateSectionStatus(sectionTitle: String, status: String) {
            sectionStatuses[sectionTitle] = status; instance?.refreshSections()
        }

        fun setAgent(name: String) { currentAgent = name; instance?.refreshUI() }

        fun clearAll() {
            sectionStatuses.clear(); currentComplete = 0; currentTotal = 0; currentSection = 0; totalSections = 0
            currentTitle = "Idle"; instance?.refreshUI(); instance?.refreshSections()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(9001, createNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingView()
        isShowing = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("aura_monitor", "Aura Task Monitor", NotificationManager.IMPORTANCE_LOW).apply { description = "Live task progress" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "aura_monitor").setContentTitle("⚡ Aura Active").setContentText("Task monitor running").setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun createFloatingView() {
        val p = 14
        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E80F172A"))
            setPadding(p, p, p, p)
            gravity = Gravity.CENTER_HORIZONTAL
            minimumWidth = 240
            // Rounded corners
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E80F172A"))
                cornerRadius = 24f
                setStroke(1, Color.parseColor("#334155"))
            }
        }

        // ===== HEADER ROW =====
        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

        // Pulsing Brain icon (represents AI thinking)
        headerIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_dialog_info) // Fallback
            setColorFilter(Color.parseColor("#D946EF"))
        }
        headerRow.addView(headerIcon, LinearLayout.LayoutParams(32, 32).apply { marginEnd = 8 })

        // Agent name
        agentText = TextView(this).apply {
            text = currentAgent; textSize = 10f; setTextColor(Color.parseColor("#D946EF")); typeface = Typeface.DEFAULT_BOLD
        }
        headerRow.addView(agentText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Section counter chip
        val sectionChip = TextView(this).apply {
            text = if (totalSections > 0) "§${currentSection}/$totalSections" else ""
            textSize = 8f; setTextColor(Color.parseColor("#22D3EE"))
            setPadding(8, 2, 8, 2)
            background = GradientDrawable().apply { setColor(Color.parseColor("#1A22D3EE")); cornerRadius = 8f }
        }
        headerRow.addView(sectionChip)

        floatingView?.addView(headerRow)

        // ===== TASK TITLE =====
        taskTitle = TextView(this).apply {
            text = currentTitle; textSize = 11f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            maxLines = 2; setPadding(0, 8, 0, 4)
        }
        floatingView?.addView(taskTitle)

        // ===== PROGRESS BAR =====
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = if (currentTotal > 0) currentTotal else 100
            progress = currentComplete
            progressDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#06B6D4"))
                cornerRadius = 4f
            }
            setBackgroundColor(Color.parseColor("#1E293B"))
            minimumHeight = 6
        }
        floatingView?.addView(progressBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 6).apply { setMargins(0, 4, 0, 4) })

        // ===== PROGRESS TEXT =====
        progressText = TextView(this).apply {
            text = if (currentTotal > 0) "$currentComplete of $currentTotal steps" else "Waiting..."
            textSize = 9f; setTextColor(Color.parseColor("#94A3B8")); typeface = Typeface.MONOSPACE
        }
        floatingView?.addView(progressText)

        // ===== SECTIONS CONTAINER =====
        sectionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 4)
        }
        floatingView?.addView(sectionsContainer)

        // ===== CONTROL BUTTONS =====
        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        // Pause button with icon
        val pauseBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(10, 6, 10, 6)
            background = GradientDrawable().apply { setColor(Color.parseColor("#1AFBBF24")); cornerRadius = 12f }
            setOnClickListener { Toast.makeText(context, "Type 'pause' in Neural tab", Toast.LENGTH_SHORT).show() }
        }
        pauseIcon = ImageView(this).apply { setImageResource(android.R.drawable.ic_media_pause); setColorFilter(Color.parseColor("#FBBF24")) }
        pauseBtn.addView(pauseIcon, LinearLayout.LayoutParams(14, 14).apply { marginEnd = 4 })
        pauseBtn.addView(TextView(this).apply { text = "PAUSE"; textSize = 9f; setTextColor(Color.parseColor("#FBBF24")); typeface = Typeface.DEFAULT_BOLD })
        controlRow.addView(pauseBtn)

        // Spacer
        controlRow.addView(View(this), LinearLayout.LayoutParams(8, 0))

        // Stop button
        val stopBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(10, 6, 10, 6)
            background = GradientDrawable().apply { setColor(Color.parseColor("#1AFB7185")); cornerRadius = 12f }
            setOnClickListener { Toast.makeText(context, "Type 'stop' in Neural tab", Toast.LENGTH_SHORT).show() }
        }
        stopIcon = ImageView(this).apply { setImageResource(android.R.drawable.ic_delete); setColorFilter(Color.parseColor("#FB7185")) }
        stopBtn.addView(stopIcon, LinearLayout.LayoutParams(14, 14).apply { marginEnd = 4 })
        stopBtn.addView(TextView(this).apply { text = "STOP"; textSize = 9f; setTextColor(Color.parseColor("#FB7185")); typeface = Typeface.DEFAULT_BOLD })
        controlRow.addView(stopBtn)

        floatingView?.addView(controlRow)

        // Window params - top right, draggable
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 8; y = 120 }

        windowManager?.addView(floatingView, params)
        refreshSections()
    }

    fun refreshUI() {
        taskTitle?.text = currentTitle
        progressText?.text = if (currentTotal > 0) "$currentComplete of $currentTotal steps" else "Waiting..."
        progressBar?.let {
            it.max = if (currentTotal > 0) currentTotal else 100
            it.progress = currentComplete
        }
    }

    fun refreshSections() {
        sectionsContainer?.removeAllViews()
        if (sectionStatuses.isEmpty()) {
            val tv = TextView(this).apply {
                text = "⏳ Planning tasks..."; textSize = 9f
                setTextColor(Color.parseColor("#94A3B8")); typeface = Typeface.MONOSPACE
            }
            sectionsContainer?.addView(tv)
            return
        }
        sectionStatuses.forEach { (title, status) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, 2, 0, 2)
                gravity = Gravity.CENTER_VERTICAL
            }

            // Status icon indicator
            val statusView = TextView(this).apply {
                text = when (status) {
                    "✅" -> "●"
                    "🔄" -> "◐"
                    "❌" -> "●"
                    "❓" -> "◐"
                    "⏳" -> "○"
                    else -> "○"
                }
                textSize = 8f
                setTextColor(when (status) {
                    "✅" -> Color.parseColor("#10B981")
                    "❌" -> Color.parseColor("#FB7185")
                    "❓" -> Color.parseColor("#FBBF24")
                    "🔄" -> Color.parseColor("#06B6D4")
                    else -> Color.parseColor("#64748B")
                })
                typeface = Typeface.DEFAULT_BOLD
            }
            row.addView(statusView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = 6 })

            val tv = TextView(this).apply {
                text = title; textSize = 9f; maxLines = 2
                setTextColor(when (status) {
                    "✅" -> Color.parseColor("#10B981")
                    "❌" -> Color.parseColor("#FB7185")
                    "❓" -> Color.parseColor("#FBBF24")
                    "🔄" -> Color.parseColor("#22D3EE")
                    else -> Color.parseColor("#94A3B8")
                })
                typeface = if (status == "🔄") Typeface.DEFAULT_BOLD else Typeface.MONOSPACE
            }
            row.addView(tv)
            sectionsContainer?.addView(row)
        }
    }

    override fun onDestroy() {
        floatingView?.let { windowManager?.removeView(it) }
        instance = null; isShowing = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
