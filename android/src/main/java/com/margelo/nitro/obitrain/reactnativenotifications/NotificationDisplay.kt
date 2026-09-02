package com.margelo.nitro.obitrain.reactnativenotifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

/** Renders a notification in the system tray whose tap re-enters the app with the payload as extras. */
object NotificationDisplay {
  private const val TAG = "ObiNotifications"
  const val DEFAULT_CHANNEL_ID = "default"
  private const val META_ICON = "com.google.firebase.messaging.default_notification_icon"
  private const val META_COLOR = "com.google.firebase.messaging.default_notification_color"
  private const val META_CHANNEL = "com.google.firebase.messaging.default_notification_channel_id"

  fun post(ctx: Context, payload: JSONObject) {
    val title = payload.optString("title").ifEmpty {
      payload.optJSONObject("notification")?.optString("title") ?: ""
    }
    val body = payload.optString("body").ifEmpty {
      payload.optJSONObject("notification")?.optString("body") ?: ""
    }
    val meta = runCatching {
      ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA).metaData
    }.getOrNull()
    val channelId = payload.optString("channelId").ifEmpty {
      meta?.getString(META_CHANNEL) ?: DEFAULT_CHANNEL_ID
    }
    val icon = meta?.getInt(META_ICON, 0)?.takeIf { it != 0 } ?: ctx.applicationInfo.icon

    val builder = NotificationCompat.Builder(ctx, channelId)
      .setContentTitle(title)
      .setContentText(body)
      .setSmallIcon(icon)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setContentIntent(contentIntent(ctx, payload))
    meta?.getInt(META_COLOR, 0)?.takeIf { it != 0 }?.let { builder.color = ctx.getColor(it) }

    Log.d(TAG, "posting notification on channel $channelId")
    try {
      NotificationManagerCompat.from(ctx)
        .notify(System.currentTimeMillis().toInt(), builder.build())
    } catch (e: SecurityException) {
      // POST_NOTIFICATIONS not granted (API 33+); nothing to render
      Log.w(TAG, "notification skipped: ${e.message}")
    }
  }

  private fun contentIntent(ctx: Context, payload: JSONObject): PendingIntent {
    val intent = Intent(ctx, NotificationOpenedActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      .putExtras(NotificationPayload.toExtras(payload))
    return PendingIntent.getActivity(
      ctx,
      System.currentTimeMillis().toInt(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }
}
