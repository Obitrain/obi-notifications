package com.margelo.nitro.obitrain.reactnativenotifications

import android.os.Bundle
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

/** Flat payload shape shared by every Android entry point: data keys at the root plus title/body. */
object NotificationPayload {
  const val EXTRA_MARKER = "obi.notification"
  private const val KEY_MESSAGE_ID = "google.message_id"
  private const val KEY_SENT_TIME = "google.sent_time"

  fun fromRemoteMessage(message: RemoteMessage): JSONObject {
    val json = JSONObject()
    for ((key, value) in message.data) json.put(key, value)
    message.notification?.let { n ->
      n.title?.let { json.put("title", it) }
      n.body?.let { json.put("body", it) }
    }
    message.messageId?.let { json.put(KEY_MESSAGE_ID, it) }
    if (message.sentTime != 0L) json.put(KEY_SENT_TIME, message.sentTime)
    return json
  }

  /** True for extras written by FCM (system-tray tap) or by our own content intents. */
  fun isNotificationExtras(extras: Bundle): Boolean =
    extras.containsKey(KEY_MESSAGE_ID) ||
      extras.containsKey(KEY_SENT_TIME) ||
      extras.containsKey(EXTRA_MARKER)

  fun fromExtras(extras: Bundle): JSONObject {
    val json = JSONObject()
    for (key in extras.keySet()) {
      if (key == EXTRA_MARKER) continue
      if (key == KEY_SENT_TIME) {
        json.put(key, extras.getLong(key))
      } else {
        extras.getString(key)?.let { json.put(key, it) }
      }
    }
    return json
  }

  /** Content-intent extras: every scalar at the root as a string, plus the marker. */
  fun toExtras(payload: JSONObject): Bundle {
    val extras = Bundle()
    for (key in payload.keys()) {
      val value = payload.opt(key) ?: continue
      extras.putString(key, if (value is String) value else value.toString())
    }
    extras.putString(EXTRA_MARKER, "1")
    return extras
  }
}
