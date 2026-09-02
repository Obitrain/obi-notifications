package com.margelo.nitro.obitrain.reactnativenotifications

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Invisible trampoline behind every notification the library posts. It runs in the app
 * process, so it can hand the payload to JS directly (app alive) or park it for
 * getInitialNotification (app killed), then brings the launcher activity forward.
 * This sidesteps the singleTask relaunch case where Android recreates the activity with
 * its old intent and React Native drops the new one because its context is not ready.
 */
class NotificationOpenedActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handle(intent)
    finish()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handle(intent)
    finish()
  }

  private fun handle(intent: Intent?) {
    val extras = intent?.extras
    if (extras != null && NotificationPayload.isNotificationExtras(extras)) {
      val json = NotificationPayload.fromExtras(extras).toString()
      if (NotificationEvents.hasOpenedListener()) {
        Log.d(TAG, "notification opened, app alive")
        NotificationEvents.emitOpened(json)
      } else {
        Log.d(TAG, "notification opened, app cold")
        NotificationEvents.initialNotification = json
      }
    }
    packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
      launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      startActivity(launch)
    }
  }

  private companion object {
    const val TAG = "ObiNotifications"
  }
}
