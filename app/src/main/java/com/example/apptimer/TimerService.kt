package com.example.apptimer

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.accessibility.AccessibilityEvent

class TimerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null
    private val timeUp = Runnable { onTimeUp() }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            handler.removeCallbacks(timeUp)
            currentPkg = null
        }
    }

    override fun onServiceConnected() {
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        val ch = NotificationChannel("timeup", "Time's up alerts", NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        // Ignore notification shade and keyboards so they don't reset the timer
        if (pkg == "com.android.systemui" || pkg.contains("inputmethod")) return
        if (pkg == currentPkg) return
        currentPkg = pkg
        handler.removeCallbacks(timeUp)
        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        val apps = p.getStringSet("apps", emptySet()) ?: emptySet()
        if (apps.contains(pkg)) {
            val mins = p.getInt("limit", 15)
            handler.postDelayed(timeUp, mins * 60_000L)
        }
    }

    private fun onTimeUp() {
        val mins = getSharedPreferences("cfg", MODE_PRIVATE).getInt("limit", 15)
        currentPkg = null
        try {
            val r = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            r.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            r.play()
            handler.postDelayed({ r.stop() }, 5000)
        } catch (_: Exception) {}
        try {
            getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500, 300, 500), -1))
        } catch (_: Exception) {}
        val n = Notification.Builder(this, "timeup")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time's up!")
            .setContentText("You've been on this app for $mins minutes. Take a break.")
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(1, n)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(timeUp)
        try { unregisterReceiver(screenOff) } catch (_: Exception) {}
        super.onDestroy()
    }
}
