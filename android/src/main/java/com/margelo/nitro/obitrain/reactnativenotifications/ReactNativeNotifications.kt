package com.margelo.nitro.obitrain.reactnativenotifications

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.proguard.annotations.DoNotStrip
import com.facebook.react.bridge.ActivityEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.Promise
import org.json.JSONObject

@DoNotStrip
class ReactNativeNotifications : HybridReactNativeNotificationsSpec() {
  private var registrationFailedCallback: ((RegistrationErrorEvent) -> Unit)? = null
  private val mainHandler = Handler(Looper.getMainLooper())

  private val context: Context?
    get() = NitroModules.applicationContext

  init {
    // warm taps: the tray intent re-enters the (singleTask) activity through onNewIntent
    NitroModules.applicationContext?.addActivityEventListener(object : ActivityEventListener {
      override fun onNewIntent(intent: Intent) {
        val extras = intent.extras ?: return
        if (NotificationPayload.isNotificationExtras(extras)) {
          Log.d(TAG, "notification opened via onNewIntent")
          NotificationEvents.emitOpened(NotificationPayload.fromExtras(extras).toString())
        }
      }

      override fun onActivityResult(
        activity: Activity, requestCode: Int, resultCode: Int, data: Intent?
      ) = Unit
    })
  }

  override fun registerRemoteNotifications(): Promise<Unit> {
    val promise = Promise<Unit>()
    fetchToken(promise, 0)
    return promise
  }

  @Suppress("DEPRECATION")
  private fun fetchToken(promise: Promise<Unit>, retryCount: Int) {
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
      if (task.isSuccessful) {
        NotificationEvents.emitToken(task.result)
        promise.resolve(Unit)
        return@addOnCompleteListener
      }
      if (retryCount < TOKEN_FETCH_MAX_RETRIES) {
        val retryDelay = TOKEN_FETCH_RETRY_DELAY_MS * (1L shl retryCount)
        mainHandler.postDelayed(
          { fetchToken(promise, retryCount + 1) },
          retryDelay
        )
        return@addOnCompleteListener
      }
      registrationFailedCallback?.invoke(
        RegistrationErrorEvent(
          code = "fcm-token-fetch-failed",
          domain = "com.google.firebase.messaging",
          localizedDescription = task.exception?.message ?: "Unknown FCM token error"
        )
      )
      promise.resolve(Unit)
    }
  }

  override fun getInitialNotification(): Promise<String?> {
    // our own tray entries go through the trampoline; FCM-rendered ones land in the
    // launcher activity's intent extras
    NotificationEvents.initialNotification?.let {
      NotificationEvents.initialNotification = null
      return Promise.resolved(it)
    }
    val extras = NitroModules.applicationContext?.currentActivity?.intent?.extras
    val json = extras
      ?.takeIf { NotificationPayload.isNotificationExtras(it) }
      ?.let { NotificationPayload.fromExtras(it).toString() }
    return Promise.resolved(json)
  }

  override fun postLocalNotification(payloadJson: String) {
    val ctx = context ?: return
    val payload = runCatching { JSONObject(payloadJson) }.getOrNull() ?: JSONObject()
    NotificationDisplay.post(ctx, payload)
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
    NotificationEvents.setTokenCallback(callback)
  }

  override fun onRegistrationFailed(callback: (RegistrationErrorEvent) -> Unit) {
    registrationFailedCallback = callback
  }

  // Android has no OS completion block to honour, so the JS promises are awaited only to
  // surface rejections instead of leaking them.
  override fun onNotificationReceivedForeground(
    callback: (String) -> Promise<Promise<NotificationCompletion>>
  ) {
    NotificationEvents.setForegroundCallback { json -> callback(json).settle() }
  }

  override fun onNotificationOpened(callback: (String) -> Promise<Promise<Unit>>) {
    NotificationEvents.setOpenedCallback { json -> callback(json).settle() }
  }

  override fun onNotificationReceivedBackground(
    callback: (String) -> Promise<Promise<BackgroundFetchResult>>
  ) {
    NotificationEvents.setBackgroundCallback { json -> callback(json).settle() }
  }

  private companion object {
    const val TAG = "ObiNotifications"
    const val TOKEN_FETCH_MAX_RETRIES = 3
    const val TOKEN_FETCH_RETRY_DELAY_MS = 10_000L
  }

  private fun <T> Promise<Promise<T>>.settle() {
    then { inner -> inner.catch { } }.catch { }
  }
}
