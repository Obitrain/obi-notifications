package com.margelo.nitro.obitrain.reactnativenotifications

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM entry point. Foregrounded app: hand the message to JS (which decides how to show it).
 * Otherwise render it natively (FCM only auto-displays notification messages, never data-only
 * ones) and tell JS if it happens to be running.
 */
class ObiMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    NotificationEvents.emitToken(token)
  }

  override fun onMessageReceived(message: RemoteMessage) {
    val payload = NotificationPayload.fromRemoteMessage(message)
    val foreground = isAppInForeground()
    Log.d(TAG, "message ${message.messageId} received, foreground=$foreground")
    if (foreground) {
      NotificationEvents.emitForeground(payload.toString())
    } else {
      NotificationDisplay.post(this, payload)
      NotificationEvents.emitBackground(payload.toString())
    }
  }

  private companion object {
    const val TAG = "ObiNotifications"
  }

  private fun isAppInForeground(): Boolean {
    val info = RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(info)
    return info.importance <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND
  }
}
