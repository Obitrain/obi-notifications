import Foundation
import UIKit
import UserNotifications

/**
 * OS-level notification integration, deliberately free of any Nitro/C++
 * types so plain-Swift app code (AppDelegate) can import this module.
 *
 * The host app's AppDelegate must:
 *   - call `ObiNotificationsCenter.attach()` in didFinishLaunching
 *   - forward `didRegisterForRemoteNotificationsWithDeviceToken` /
 *     `didFailToRegisterForRemoteNotificationsWithError` /
 *     `didReceiveRemoteNotification` to the static funcs below.
 * No swizzling is performed.
 */
public final class ObiNotificationsCenter {
  public static let shared = ObiNotificationsCenter()

  // internal NSObject adapter carries the UNUserNotificationCenterDelegate conformance
  private let delegateAdapter = NotificationDelegateAdapter()

  // Handlers installed by the Nitro bridge layer. Plain types only.
  public var tokenHandler: ((String) -> Void)?
  /** code, domain, localizedDescription */
  public var registrationFailedHandler: ((String, String, String) -> Void)?
  /** payload JSON + completion(alert, sound, badge) */
  public var foregroundHandler: ((String, @escaping (Bool, Bool, Bool) -> Void) -> Void)?
  /** payload JSON + completion() */
  public var openedHandler: ((String, @escaping () -> Void) -> Void)?
  /** payload JSON + completion("newData" | "noData" | "failed") */
  public var backgroundHandler: ((String, @escaping (String) -> Void) -> Void)?

  public var lastToken: String?
  public var initialNotificationJson: String?

  private init() {}

  // AppDelegate entry points

  /** Install as UNUserNotificationCenter delegate. Call in didFinishLaunching. */
  public static func attach() {
    UNUserNotificationCenter.current().delegate = shared.delegateAdapter
  }

  public static func didRegisterForRemoteNotifications(deviceToken: Data) {
    let token = deviceToken.map { String(format: "%02x", $0) }.joined()
    shared.lastToken = token
    shared.tokenHandler?(token)
  }

  public static func didFailToRegisterForRemoteNotifications(error: Error) {
    let nsError = error as NSError
    shared.registrationFailedHandler?(
      String(nsError.code), nsError.domain, nsError.localizedDescription)
  }

  /** Remote notification delivered while backgrounded (content-available). */
  public static func didReceiveRemoteNotification(
    userInfo: [AnyHashable: Any],
    completionHandler: @escaping (UIBackgroundFetchResult) -> Void
  ) {
    guard let handler = shared.backgroundHandler else {
      completionHandler(.noData)
      return
    }
    // the OS kills background fetch after ~30s; stay under it
    let done = Deadline(seconds: 25) { completionHandler(.noData) }
    handler(toJson(userInfo)) { result in
      guard done.tryComplete() else { return }
      switch result {
      case "newData": completionHandler(.newData)
      case "failed": completionHandler(.failed)
      default: completionHandler(.noData)
      }
    }
  }

  // Called by NotificationDelegateAdapter

  func willPresent(
    _ notification: UNNotification,
    completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    guard let handler = foregroundHandler else {
      completionHandler([])
      return
    }
    let done = Deadline(seconds: 4) { completionHandler([]) }
    handler(Self.toJson(notification.request.content.userInfo)) { alert, sound, badge in
      guard done.tryComplete() else { return }
      var options: UNNotificationPresentationOptions = []
      if alert { options.insert([.banner, .list]) }
      if sound { options.insert(.sound) }
      if badge { options.insert(.badge) }
      completionHandler(options)
    }
  }

  func didReceive(
    _ response: UNNotificationResponse,
    completionHandler: @escaping () -> Void
  ) {
    guard response.actionIdentifier == UNNotificationDefaultActionIdentifier else {
      completionHandler()
      return
    }
    let json = Self.toJson(response.notification.request.content.userInfo)
    guard let handler = openedHandler else {
      // JS not attached yet: this tap cold-started the app
      initialNotificationJson = json
      completionHandler()
      return
    }
    let done = Deadline(seconds: 4) { completionHandler() }
    handler(json) {
      if done.tryComplete() { completionHandler() }
    }
  }

  static func toJson(_ userInfo: [AnyHashable: Any]) -> String {
    guard JSONSerialization.isValidJSONObject(userInfo),
      let data = try? JSONSerialization.data(withJSONObject: userInfo),
      let json = String(data: data, encoding: .utf8)
    else {
      return "{}"
    }
    return json
  }
}

/** Internal on purpose: keeps the ObjC protocol conformance out of the public API. */
final class NotificationDelegateAdapter: NSObject, UNUserNotificationCenterDelegate {
  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    ObiNotificationsCenter.shared.willPresent(notification, completionHandler: completionHandler)
  }

  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    didReceive response: UNNotificationResponse,
    withCompletionHandler completionHandler: @escaping () -> Void
  ) {
    ObiNotificationsCenter.shared.didReceive(response, completionHandler: completionHandler)
  }
}

/** Ensures an OS completion handler is called exactly once, with a timeout fallback. */
final class Deadline {
  private let lock = NSLock()
  private var completed = false

  init(seconds: TimeInterval, onTimeout: @escaping () -> Void) {
    // strong capture: the timeout must fire even if nothing else retains us
    DispatchQueue.main.asyncAfter(deadline: .now() + seconds) {
      if self.tryComplete() {
        onTimeout()
      }
    }
  }

  /** Returns true exactly once. */
  func tryComplete() -> Bool {
    lock.lock()
    defer { lock.unlock() }
    if completed { return false }
    completed = true
    return true
  }
}
