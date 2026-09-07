import Foundation
import NitroModules
import ObiNotificationsCore
import UIKit
import UserNotifications

/** Nitro bridge: adapts JS callbacks/promises onto ObiNotificationsCenter. */
class ReactNativeNotifications: HybridReactNativeNotificationsSpec {
  private var center: ObiNotificationsCenter { ObiNotificationsCenter.shared }

  func registerRemoteNotifications() throws -> Promise<Void> {
    let promise = Promise<Void>()
    UNUserNotificationCenter.current().requestAuthorization(options: [
      .alert, .badge, .sound,
    ]) { granted, error in
      if let error {
        ObiNotificationsCenter.didFailToRegisterForRemoteNotifications(error: error)
        promise.resolve()
        return
      }
      if granted {
        DispatchQueue.main.async {
          UIApplication.shared.registerForRemoteNotifications()
        }
      }
      promise.resolve()
    }
    return promise
  }

  func getInitialNotification() throws -> Promise<String?> {
    let json = center.initialNotificationJson
    center.initialNotificationJson = nil
    return Promise.resolved(withResult: json)
  }

  func postLocalNotification(payloadJson: String) throws {
    guard let data = payloadJson.data(using: .utf8),
      let payload = try JSONSerialization.jsonObject(with: data) as? [String: Any]
    else { throw NSError(domain: "ObiNotifications", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid local notification payload"]) }
    let content = UNMutableNotificationContent()
    content.title = payload["title"] as? String ?? ""
    content.body = payload["body"] as? String ?? ""
    content.sound = .default
    content.userInfo = payload
    let request = UNNotificationRequest(
      identifier: payload["id"] as? String ?? UUID().uuidString,
      content: content,
      trigger: UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false))
    let notifications = UNUserNotificationCenter.current()
    notifications.requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
      if let error { NSLog("Local notification permission failed: %@", error.localizedDescription); return }
      guard granted else { return }
      notifications.add(request) { error in
        if let error { NSLog("Local notification failed: %@", error.localizedDescription) }
      }
    }
  }

  func setNotificationChannel(channel: NotificationChannelConfig) throws {
    // Android-only API.
  }

  func onTokenReceived(callback: @escaping (String) -> Void) throws {
    center.tokenHandler = callback
    // replay so registration order doesn't matter
    if let token = center.lastToken {
      callback(token)
    }
  }

  func onRegistrationFailed(callback: @escaping (RegistrationErrorEvent) -> Void) throws {
    center.registrationFailedHandler = { code, domain, message in
      callback(
        RegistrationErrorEvent(code: code, domain: domain, localizedDescription: message))
    }
  }

  func onNotificationReceivedForeground(
    callback: @escaping (String) -> Promise<Promise<NotificationCompletion>>
  ) throws {
    center.foregroundHandler = { json, completion in
      callback(json).then { inner in
        inner.then { result in
          completion(result.alert == true, result.sound == true, result.badge == true)
        }.catch { _ in completion(false, false, false) }
      }.catch { _ in completion(false, false, false) }
    }
  }

  func onNotificationOpened(callback: @escaping (String) -> Promise<Promise<Void>>) throws {
    center.openedHandler = { json, completion in
      callback(json).then { inner in
        inner.then { completion() }.catch { _ in completion() }
      }.catch { _ in completion() }
    }
  }

  func onNotificationReceivedBackground(
    callback: @escaping (String) -> Promise<Promise<BackgroundFetchResult>>
  ) throws {
    center.backgroundHandler = { json, completion in
      callback(json).then { inner in
        inner.then { result in
          switch result {
          case .newdata: completion("newData")
          case .nodata: completion("noData")
          case .failed: completion("failed")
          }
        }.catch { _ in completion("failed") }
      }.catch { _ in completion("failed") }
    }
  }
}
