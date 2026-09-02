package com.margelo.nitro.obitrain.reactnativenotifications

/**
 * Process-wide event bus between the OS-driven service and the JS-driven hybrid object.
 * Foreground and opened events arriving before JS registers a callback are queued and
 * replayed; background events are dropped when nobody listens (JS is not running).
 */
object NotificationEvents {
  private const val TAG = "ObiNotifications"
  private const val MAX_PENDING = 10

  @Volatile var lastToken: String? = null
    private set

  /** Payload of the tap that cold-started the app; consumed by getInitialNotification. */
  @Volatile var initialNotification: String? = null

  private var tokenCallback: ((String) -> Unit)? = null
  private var foregroundCallback: ((String) -> Unit)? = null
  private var openedCallback: ((String) -> Unit)? = null
  private var backgroundCallback: ((String) -> Unit)? = null

  private val pendingForeground = ArrayDeque<String>()
  private val pendingOpened = ArrayDeque<String>()

  @Synchronized
  fun setTokenCallback(callback: (String) -> Unit) {
    tokenCallback = callback
    lastToken?.let(callback)
  }

  @Synchronized
  fun emitToken(token: String) {
    lastToken = token
    tokenCallback?.invoke(token)
  }

  @Synchronized
  fun setForegroundCallback(callback: (String) -> Unit) {
    foregroundCallback = callback
    drain(pendingForeground, callback)
  }

  @Synchronized
  fun emitForeground(payloadJson: String) {
    android.util.Log.d(TAG, "foreground event, listener=${foregroundCallback != null}")
    foregroundCallback?.invoke(payloadJson) ?: enqueue(pendingForeground, payloadJson)
  }

  @Synchronized
  fun setOpenedCallback(callback: (String) -> Unit) {
    openedCallback = callback
    drain(pendingOpened, callback)
  }

  @Synchronized
  fun hasOpenedListener(): Boolean = openedCallback != null

  @Synchronized
  fun emitOpened(payloadJson: String) {
    android.util.Log.d(TAG, "opened event, listener=${openedCallback != null}")
    openedCallback?.invoke(payloadJson) ?: enqueue(pendingOpened, payloadJson)
  }

  @Synchronized
  fun setBackgroundCallback(callback: (String) -> Unit) {
    backgroundCallback = callback
  }

  @Synchronized
  fun emitBackground(payloadJson: String) {
    backgroundCallback?.invoke(payloadJson)
  }

  private fun enqueue(queue: ArrayDeque<String>, payloadJson: String) {
    if (queue.size >= MAX_PENDING) queue.removeFirst()
    queue.addLast(payloadJson)
  }

  private fun drain(queue: ArrayDeque<String>, callback: (String) -> Unit) {
    while (queue.isNotEmpty()) callback(queue.removeFirst())
  }
}
