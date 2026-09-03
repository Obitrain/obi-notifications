# @obitrain/react-native-notifications

Push notifications for obiapp as a [Nitro module](https://nitro.margelo.com/): raw APNs on iOS, Firebase Cloud Messaging (FCM) on Android. Kotlin and Swift, React Native 0.87 with the New Architecture, no Expo.

The library replaces wix `react-native-notifications` and uses the same JS syntax. It is built for a single consumer with a single configuration: there are no feature flags, podspec subspecs, or optional dependencies.

## Requirements

| Requirement | Value |
| --- | --- |
| React Native | 0.87.x, New Architecture enabled |
| `react-native-nitro-modules` | ^0.36.5 (peer dependency) |
| iOS | 15.1+, `aps-environment` entitlement on the app target |
| Android | minSdk 24, `com.google.gms.google-services` plugin and a `google-services.json` in the app |

## Installation

```sh
yarn add @obitrain/react-native-notifications react-native-nitro-modules
```

Autolinking registers the Nitro module on both platforms. iOS needs two manual steps; Android needs none.

### iOS

The iOS side ships as two CocoaPods:

- `ObiNotificationsCore` (`ios/core/`) is pure Swift. It owns the `UNUserNotificationCenterDelegate`, APNs token forwarding, and the initial-notification capture. It has no C++ types, so the app's Swift `AppDelegate` can import it.
- `ReactNativeNotifications` is the Nitro pod. Autolinking adds it; it depends on the core pod.

Why two pods: a nitrogen-generated pod exposes C++ headers through its clang module, which makes it un-importable from app Swift unless C++ interop is enabled app-wide. Keeping the delegate in a plain-Swift pod avoids that.

1. Add the core pod to `ios/Podfile` inside the app target, then run `pod install`:

   ```ruby
   pod 'ObiNotificationsCore', :path => '../node_modules/@obitrain/react-native-notifications/ios/core'
   ```

2. Attach the delegate before React Native starts and forward the three `UIApplicationDelegate` notification callbacks. The library does not swizzle; every hook is explicit:

   ```swift
   import ObiNotificationsCore

   // in application(_:didFinishLaunchingWithOptions:), before startReactNative
   ObiNotificationsCenter.attach()

   func application(_ application: UIApplication,
                    didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
     ObiNotificationsCenter.didRegisterForRemoteNotifications(deviceToken: deviceToken)
   }

   func application(_ application: UIApplication,
                    didFailToRegisterForRemoteNotificationsWithError error: Error) {
     ObiNotificationsCenter.didFailToRegisterForRemoteNotifications(error: error)
   }

   func application(_ application: UIApplication,
                    didReceiveRemoteNotification userInfo: [AnyHashable: Any],
                    fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
     ObiNotificationsCenter.didReceiveRemoteNotification(userInfo: userInfo,
                                                         completionHandler: completionHandler)
   }
   ```

   `attach()` makes the library the `UNUserNotificationCenter` delegate. If another SDK replaces that delegate later, foreground and opened events stop arriving.

   A complete, working `AppDelegate` is in [`example/ios/ReactNativeNotificationsExample/AppDelegate.swift`](example/ios/ReactNativeNotificationsExample/AppDelegate.swift).

3. For silent (`content-available`) pushes, the app's `Info.plist` needs `UIBackgroundModes` containing `remote-notification`.

### Android

Nothing beyond autolinking. The library depends on `firebase-messaging` directly, registers its own `FirebaseMessagingService` through manifest merging, and declares `POST_NOTIFICATIONS`. Requesting that permission at runtime on Android 13+ is the app's responsibility; the library does not prompt, and notifications are silently skipped while it is denied.

Notifications rendered by the library use the app's FCM manifest meta-data when present: `com.google.firebase.messaging.default_notification_icon`, `default_notification_color`, and `default_notification_channel_id` (falling back to the app icon and the `default` channel).

How an incoming FCM message is routed:

| App state | Message | What happens |
| --- | --- | --- |
| foreground | any | `registerNotificationReceivedForeground` fires; the app decides what to show (obiapp calls `postLocalNotification`) |
| background or killed | data-only | the library posts it to the system tray; `registerNotificationReceivedBackground` fires if JS is running |
| background or killed | with `notification` block | FCM itself posts it; the library is not involved until the tap |

Taps are handled two ways, depending on who rendered the notification:

- **Posted by the library** (data-only messages, `postLocalNotification`): the tap runs an invisible trampoline activity inside the app process. App alive: `registerNotificationOpened` fires. App killed: the payload is parked and `getInitialNotification()` returns it after the cold start; no opened event fires.
- **Rendered by FCM** (messages with a `notification` block while backgrounded): the tap re-enters the launcher activity with the data as intent extras. App alive: `registerNotificationOpened` fires through `onNewIntent`. App killed: `getInitialNotification()` reads the activity's intent. One gap remains: if the process was killed but the task is still in recents, Android recreates the activity with its *old* intent and delivers the new one through `onNewIntent` before React Native is ready, so the payload is lost. Close it with three lines in `MainActivity`:

  ```kotlin
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent) // keeps FCM tap extras readable by getInitialNotification after a relaunch
  }
  ```

The library inherits `ndkVersion` from the root project rather than pinning its own, because a mismatch with the app's NDK fails at startup with a libc++ symbol error (`__cxa_init_primary_exception`).

## Usage

```ts
import {
  Notifications,
  NotificationBackgroundFetchResult,
} from '@obitrain/react-native-notifications';

const events = Notifications.events();

events.registerRemoteNotificationsRegistered(({ deviceToken }) => {
  // iOS: APNs token as lowercase hex. Android: Firebase Installation ID.
});

events.registerRemoteNotificationsRegistrationFailed(({ code, domain, localizedDescription }) => {});

events.registerNotificationReceivedForeground((notification, completion) => {
  const data = notification.payload?.aps?.data ?? notification.payload;
  completion({ alert: true, sound: true, badge: false });
});

events.registerNotificationOpened((notification, completion) => {
  completion();
});

events.registerNotificationReceivedBackground((notification, completion) => {
  completion(NotificationBackgroundFetchResult.NEW_DATA);
});

// iOS: prompts for alert, badge, and sound permission, then registers with APNs.
// Android: registers the Firebase Installation ID with bounded retries.
// The identifier arrives through the registered event, not the returned promise.
await Notifications.registerRemoteNotifications();

// The notification whose tap launched the app, or undefined.
const initial = await Notifications.getInitialNotification();
```

### API

| Member | Platform | Behaviour |
| --- | --- | --- |
| `registerRemoteNotifications(): Promise<void>` | both | iOS: requests alert, badge, and sound permission, then calls `registerForRemoteNotifications`. Resolves when the permission prompt completes, before the token arrives. Android: registers the Firebase Installation ID with FCM, retrying failures after 10, 20, and 40 seconds. |
| `getInitialNotification(): Promise<Notification \| undefined>` | both | Payload of the notification whose tap launched the app, `undefined` on a normal launch. iOS returns it once and then clears it. Android reads the launch intent's extras and returns them whenever they carry a `google.message_id` or `google.sent_time` key. |
| `postLocalNotification(payload)` | Android | Posts a notification immediately with `payload.title` and `payload.body` (or `payload.notification.title` / `.body`) on `payload.channelId`, else the FCM default channel from the manifest, else `default`. Create the channel with `setNotificationChannel` first; Android 8+ drops notifications on unknown channels. Tapping it re-enters the app with the payload (see the Android section). Used to display FCM messages that arrive while the app is in the foreground, which FCM does not display itself. |
| `setNotificationChannel(config)` | Android | Creates or updates a notification channel. `importance` takes `NotificationManager` values 0 to 5. |
| `events()` | both | Returns the event registrars below. |

| Event registrar | Callback |
| --- | --- |
| `registerRemoteNotificationsRegistered` | `({ deviceToken }) => void` |
| `registerRemoteNotificationsRegistrationFailed` | `({ code, domain, localizedDescription }) => void` |
| `registerNotificationReceivedForeground` | `(notification, completion: (r: { alert?, sound?, badge? }) => void) => void` |
| `registerNotificationOpened` | `(notification, completion: () => void) => void` |
| `registerNotificationReceivedBackground` | `(notification, completion: (r: NotificationBackgroundFetchResult) => void) => void` |

Each registrar replaces the previous callback for that event. A registration identifier received before `registerRemoteNotificationsRegistered` is called is replayed on registration. On Android, foreground and opened events that arrive before their callback is registered are queued (last 10) and replayed; background events are dropped when no callback is registered, since the app process may have been started by FCM without JS. On iOS, register callbacks before calling `registerRemoteNotifications()`.

### Payload shape

`notification.payload` is the APNs `userInfo` dictionary on iOS, so custom data sent under `aps.data` is reachable as `payload.aps.data`. On Android it is a flat object: every FCM `data` key at the root, plus `title` and `body` from the message's `notification` block when present, plus `google.message_id` and `google.sent_time`. The read `notification.payload?.aps?.data ?? notification.payload` therefore yields the custom data on both platforms.

Payloads cross the Nitro boundary as JSON strings and are parsed in the JS wrapper. A payload that fails to parse becomes `{}`.

### Completion handlers and deadlines

The foreground, opened, and background callbacks receive a `completion` function. Native code waits for it before calling the OS completion block. If JS does not call `completion` in time, native completes on its own so the OS deadline is never blown:

| Event | Deadline | Fallback |
| --- | --- | --- |
| foreground (`willPresent`) | 4 s | present nothing |
| opened (`didReceive`) | 4 s | complete |
| background fetch | 25 s | `noData` |

## Limitations

- Android runs no JS when the app is killed: a data-only message is shown in the tray, but `registerNotificationReceivedBackground` does not fire until the app is next running. There is no headless JS task.
- On Android, `registerNotificationReceivedBackground`'s completion value is accepted and ignored; only iOS has a fetch completion block.
- Notifications rendered by FCM itself, tapped after a process kill while the task survives in recents, reach `getInitialNotification()` only with the `MainActivity` override shown in the Android section.
- On iOS, the cold-start tap path and the `content-available` background path are wired but unverified on a device. Simulator APNs tokens are 80 bytes; physical devices return 32 bytes.
- Out of scope by design: scheduled or local reminders, action buttons and categories, badge management, notification grouping, rich media attachments, and an inbox or history API.

## Development

```sh
yarn            # installs the workspace and the example app
yarn nitrogen   # regenerates nitrogen/ from src/*.nitro.ts
yarn typecheck
yarn lint
```

The example app lives in `example/`. Start Metro with `yarn example start` and run a platform with `yarn example ios` or `yarn example android`.

The Android example uses `applicationId com.obitrain.obiapp.dev.release` so that obiapp's Firebase project accepts it. Copy obiapp's `android/app/google-services.json` into `example/android/app/` to run it; the file is gitignored.

On the iOS simulator, `xcrun simctl push <UDID> payload.apns` delivers a notification to the running example, where `payload.apns` includes a `"Simulator Target Bundle"` key set to the example's bundle id.

## License

MIT
