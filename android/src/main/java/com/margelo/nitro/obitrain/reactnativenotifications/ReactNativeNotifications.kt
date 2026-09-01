package com.margelo.nitro.obitrain.reactnativenotifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.facebook.proguard.annotations.DoNotStrip
import com.google.firebase.messaging.FirebaseMessaging
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.Promise
import org.json.JSONObject

@DoNotStrip
class ReactNativeNotifications : HybridReactNativeNotificationsSpec() {
  private var tokenCallback: ((String) -> Unit)? = null
  private var registrationFailedCallback: ((RegistrationErrorEvent) -> Unit)? = null
  private var foregroundCallback: ((String) -> Promise<Promise<NotificationCompletion>>)? = null
  private var openedCallback: ((String) -> Promise<Promise<Unit>>)? = null
  private var backgroundCallback: ((String) -> Promise<Promise<BackgroundFetchResult>>)? = null

  private var lastToken: String? = null

  private val context: Context?
    get() = NitroModules.applicationContext

  override fun registerRemoteNotifications(): Promise<Unit> {
    val promise = Promise<Unit>()
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
      if (task.isSuccessful) {
        val token = task.result
        lastToken = token
        tokenCallback?.invoke(token)
      } else {
        val error = task.exception
        registrationFailedCallback?.invoke(
          RegistrationErrorEvent(
            code = "fcm-token-fetch-failed",
            domain = "com.google.firebase.messaging",
            localizedDescription = error?.message ?: "Unknown FCM token error"
          )
        )
      }
      promise.resolve(Unit)
    }
    return promise
  }

  override fun getInitialNotification(): Promise<String?> {
    val extras = NitroModules.applicationContext?.currentActivity?.intent?.extras
    val json = extras?.takeIf { isRemoteNotificationExtras(it) }?.let { bundleToJson(it) }
    return Promise.resolved(json)
  }

  override fun postLocalNotification(payloadJson: String) {
    val ctx = context ?: return
    val payload = runCatching { JSONObject(payloadJson) }.getOrNull() ?: JSONObject()
    val title = payload.optString("title").ifEmpty {
      payload.optJSONObject("notification")?.optString("title") ?: ""
    }
    val body = payload.optString("body").ifEmpty {
      payload.optJSONObject("notification")?.optString("body") ?: ""
    }
    val builder = NotificationCompat.Builder(ctx, DEFAULT_CHANNEL_ID)
      .setContentTitle(title)
      .setContentText(body)
      .setSmallIcon(ctx.applicationInfo.icon)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
    try {
      NotificationManagerCompat.from(ctx)
        .notify(System.currentTimeMillis().toInt(), builder.build())
    } catch (e: SecurityException) {
      // POST_NOTIFICATIONS not granted (API 33+); nothing to render
      Log.w(TAG, "postLocalNotification skipped: ${e.message}")
    }
  }

  override fun setNotificationChannel(channel: NotificationChannelConfig) {
    val ctx = context ?: return
    val importance = channel.importance?.toInt() ?: NotificationManager.IMPORTANCE_DEFAULT
    val nativeChannel = NotificationChannel(channel.channelId, channel.name, importance).apply {
      channel.description?.let { description = it }
      channel.enableLights?.let { enableLights(it) }
      channel.enableVibration?.let { enableVibration(it) }
      channel.showBadge?.let { setShowBadge(it) }
      channel.vibrationPattern?.let { pattern ->
        vibrationPattern = pattern.map { it.toLong() }.toLongArray()
      }
    }
    val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(nativeChannel)
  }

  override fun onTokenReceived(callback: (String) -> Unit) {
    tokenCallback = callback
    // replay so registration order doesn't matter
    lastToken?.let { callback(it) }
  }

  override fun onRegistrationFailed(callback: (RegistrationErrorEvent) -> Unit) {
    registrationFailedCallback = callback
  }

  override fun onNotificationReceivedForeground(
    callback: (String) -> Promise<Promise<NotificationCompletion>>
  ) {
    foregroundCallback = callback
  }

  override fun onNotificationOpened(callback: (String) -> Promise<Promise<Unit>>) {
    openedCallback = callback
  }

  override fun onNotificationReceivedBackground(
    callback: (String) -> Promise<Promise<BackgroundFetchResult>>
  ) {
    backgroundCallback = callback
  }

  private fun isRemoteNotificationExtras(extras: Bundle): Boolean =
    extras.containsKey("google.message_id") || extras.containsKey("google.sent_time")

  private fun bundleToJson(bundle: Bundle): String {
    val json = JSONObject()
    for (key in bundle.keySet()) {
      @Suppress("DEPRECATION")
      json.put(key, JSONObject.wrap(bundle.get(key)) ?: JSONObject.NULL)
    }
    return json.toString()
  }

  companion object {
    private const val TAG = "ObiNotifications"
    const val DEFAULT_CHANNEL_ID = "default"
  }
}
