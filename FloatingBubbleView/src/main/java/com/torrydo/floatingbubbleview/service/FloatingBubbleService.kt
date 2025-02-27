package com.torrydo.floatingbubbleview.service

import android.accessibilityservice.AccessibilityService
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.torrydo.floatingbubbleview.canDrawOverlays
import com.torrydo.floatingbubbleview.helper.NotificationHelper
import com.torrydo.floatingbubbleview.service.expandable.BubbleBuilder
import com.torrydo.floatingbubbleview.service.expandable.ExpandedBubbleBuilder
import com.torrydo.floatingbubbleview.sez


abstract class FloatingBubbleService : AccessibilityService() {

    open fun startNotificationForeground() {
        val noti = NotificationHelper(this)
        noti.createNotificationChannel()
        startForeground(noti.notificationId, noti.defaultNotification())
    }

    // override fun onBind(intent: Intent?): IBinder? = null
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        handleAccessibilityEvent(event)
    }

    override fun onCreate() {
        super.onCreate()

//        if (canDrawOverlays().not()) {
//            throw SecurityException("Permission Denied: \"display over other app\" permission IS NOT granted!")
//        }
        // Reduce to a warning. User may give this permission at a later point
        Log.d("FloatingBubbleService", "Permission Denied: \"display over other app\" permission IS NOT granted!")

        sez.with(this.applicationContext)

        startNotificationForeground()
        setup()
    }

    abstract fun setup()

    abstract fun removeAll()

    abstract fun handleAccessibilityEvent(event: AccessibilityEvent)

    override fun onDestroy() {
        removeAll()
        super.onDestroy()
    }

}