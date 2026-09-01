# @obitrain/react-native-notifications — plan

Replacement for wix `react-native-notifications` 5.2.2 (dead upstream). Nitro module,
Kotlin + Swift, RN 0.87.1 / New Architecture, `react-native-nitro-modules ^0.36.5`
(matches obiapp's pin). No Expo, no Firebase on iOS (raw APNs), FCM on Android.
Single consumer (obiapp), single configuration — no feature flags, subspecs, or plugin
machinery.

## Status (this session)

Done and proven on device:

- Scaffolded with `create-react-native-library` (nitro-module template) at
  `~/Projects/Obitrain/obi-notifications`.
- Nitro spec (`src/ReactNativeNotifications.nitro.ts`) covers the full obiapp surface:
  `registerRemoteNotifications`, `getInitialNotification`, `postLocalNotification`,
  `setNotificationChannel`, and the 5 events. Payloads cross the boundary as JSON
  strings; the JS wrapper (`src/index.tsx`) exposes the wix-compatible API
  (`Notifications`, `events()`, `NotificationBackgroundFetchResult`, types).
- **Android (proven on Play-services emulator)**: build + install clean; tap
  "register" → FCM token fetched and delivered to JS via the Nitro callback;
  `registerRemoteNotifications()` resolves; channel creation runs;
  `getInitialNotification()` resolves `undefined` on a plain launch.
- **iOS (proven on iPhone 16 Pro simulator, iOS 18.5)**: build + install clean;
  permission dialog → grant → `registerForRemoteNotifications` → **raw APNs token
  (hex) delivered to JS**; registration-failure event proven (missing-entitlement case
  surfaced `NSCocoaErrorDomain 3000` to JS); foreground receive proven end-to-end via
  `simctl push` — JS callback got the payload, resolved `{alert:true,sound:true}`,
  and the **native banner presented** (the completion-handler-over-Nitro bridge, the
  riskiest piece, works); notification **opened** event proven (tap on banner → JS
  callback with payload).
- iOS AppDelegate integration is explicit forwarding, **no swizzling** (see
  "iOS integration contract" below).

Not yet proven / not implemented:

- iOS cold-start tap → `getInitialNotification` (code path implemented in
  `ObiNotificationsCenter.didReceive` — stores the payload when JS isn't attached —
  but the tap could not be reliably UI-automated this session; verify manually).
- iOS background `content-available` path (wired through AppDelegate forwarding;
  needs `UIBackgroundModes: remote-notification` in the app Info.plist — check obiapp).
- **All Android receive paths** (foreground receive, background/killed, tap). See
  "Remaining work". Android today only does token + channel + local-notification
  posting + initial-intent reading.
- Real-device APNs. The simulator returns real device tokens on Apple Silicon /
  macOS 13+ (proven — 80-byte simulator token), but real devices return 32-byte
  tokens through the actual APNs pipeline; verify once on hardware.

## Architecture decisions

- **Payload transport = JSON strings** at the Nitro boundary (`payloadJson`), parsed
  in the JS wrapper. Keeps nitrogen types trivial and exactly preserves obiapp's
  `notification.payload?.aps?.data ?? notification.payload` read. Nitro `AnyMap` is
  the alternative if we ever want typed maps; not worth it now.
- **Completion handlers**: spec callbacks return promises
  (`(payloadJson) => Promise<NotificationCompletion>`), which nitrogen surfaces
  natively as `(String) -> Promise<Promise<T>>` (outer = call delivery, inner = JS
  resolution). Native awaits both, then calls the OS completion handler, guarded by a
  `Deadline` (4 s for willPresent/didReceive, 25 s for background fetch) so OS
  deadlines are never blown by a hung JS side. The wix completion-callback style is
  reconstructed in the JS wrapper (`completion(response)` resolves the promise).
- **iOS is split into two pods** (both in this package):
  - `ObiNotificationsCore` — pure Swift (`ios/core/`), owns
    `UNUserNotificationCenterDelegate`, token forwarding, deadlines, initial-
    notification capture. **No Nitro/C++ types**, so a plain-Swift AppDelegate can
    `import ObiNotificationsCore`. (A nitrogen pod's clang module exposes C++
    headers, which makes it un-importable from app Swift without enabling C++
    interop app-wide — this split is the clean fix.)
  - `ReactNativeNotifications` — the Nitro pod (`ios/nitro/` + generated code),
    depends on Core, adapts JS promises onto Core's plain-closure handlers.
  - Autolinking picks the Nitro podspec via `react-native.config.js`
    (`podspecPath`); Core needs **one Podfile line** in the consumer:
    `pod 'ObiNotificationsCore', :path => '../node_modules/@obitrain/react-native-notifications'`.
- **Android**: single Gradle module; `ndkVersion` inherited from the root project
  (obiapp pins 27.1) — without this the example crashed with a libc++
  `__cxa_init_primary_exception` mismatch. `firebase-messaging` pinned to obiapp's
  BoM level (23.1.1); the app's own BoM wins if newer. `POST_NOTIFICATIONS` is
  declared in the library manifest (runtime grant is the app's business).

## iOS integration contract (no swizzling)

The host AppDelegate adds (~15 lines, see `example/ios/.../AppDelegate.swift`):

```swift
import ObiNotificationsCore
// in didFinishLaunching, before startReactNative:
ObiNotificationsCenter.attach()
// plus 3 forwarding methods:
//   didRegisterForRemoteNotificationsWithDeviceToken -> ObiNotificationsCenter.didRegisterForRemoteNotifications
//   didFailToRegisterForRemoteNotificationsWithError -> ObiNotificationsCenter.didFailToRegisterForRemoteNotifications
//   didReceiveRemoteNotification:fetchCompletionHandler -> ObiNotificationsCenter.didReceiveRemoteNotification
```

This is deliberate: explicit forwarding instead of method swizzling. It is the one
place the integration can silently break (someone else sets the
`UNUserNotificationCenter` delegate later, e.g. another SDK); grep obiapp for
competing delegates when integrating (none known today — wix's lib is the current
delegate owner and it's being removed).

## Remaining work (honest hard parts first)

1. **Android FCM service — background/killed receive and foreground receive**
   (~0.5–1 day). Add `ObiMessagingService : FirebaseMessagingService` +
   manifest-merge registration.
   - `onMessageReceived` fires for **data** messages in all states and for
     notification messages only in foreground. Route: app foregrounded → foreground
     event into JS (needs a static event bus since the OS instantiates the service;
     the hybrid object registers itself); app backgrounded/killed with data-only
     message → either post a notification natively or queue the event.
   - `onNewToken` → registered event (token rotation; today only fetch-on-register).
   - Hard part: messages arriving before JS/Nitro is up (killed state). Queue events
     in the static bus and flush when callbacks register (the token-replay pattern
     already in place shows the shape).
   - Match wix's Android payload shape: RemoteMessage `data` map + notification
     title/body flattened to top level, so obiapp's `?? payload` fallback keeps
     seeing event fields at the root.
2. **Android tap path** (~0.5 day).
   - `postLocalNotification` currently sets **no contentIntent** — taps do nothing.
     Add a PendingIntent to the launcher activity carrying the payload extras.
   - Cold start: implemented (reads `currentActivity.intent` extras, keyed on
     `google.message_id`/`google.sent_time`); extend the extras filter to our own
     local-notification marker.
   - Warm tap (app alive, activity relaunched): RN's MainActivity doesn't forward
     `onNewIntent` to libraries. Options: (a) 3-line `onNewIntent` override in
     obiapp's MainActivity calling a static (mirrors the iOS contract — preferred),
     or (b) `ActivityLifecycleCallbacks` + intent diffing (fragile). wix solved this
     inside its own ReactActivity glue; we won't ship an activity.
3. **iOS cold-start + background verification** (~0.25 day). Manually verify
   `getInitialNotification` after a cold-start tap; add
   `UIBackgroundModes: remote-notification` check for obiapp; verify the 25 s
   background-fetch deadline path.
4. **obiapp integration** (~0.5–1 day).
   - Consume via yarn `portal:`/`file:` or the private registry (do not publish
     publicly). Remove the wix dependency + its 2 patches.
   - Podfile: add the `ObiNotificationsCore` line; AppDelegate: the ~15-line
     forwarding block; MainActivity: the `onNewIntent` override (if option (a)).
   - `src/notifications.ts`: imports move to `@obitrain/react-native-notifications`;
     drop the unused third `action` argument in `registerNotificationOpened`
     (obiapp only console.logs it); everything else is signature-compatible,
     including `payload?.aps?.data ?? payload`.
   - Android 13+ `POST_NOTIFICATIONS`: obiapp never requests it at runtime today
     (wix didn't either). Decide: request inside `registerRemoteNotifications()` on
     Android, or leave to obiapp's react-native-permissions onboarding. Recommend
     the latter (explicit, matches the iOS onboarding page).
5. **Real-device pass + release hygiene** (~0.5 day). Physical iPhone (sandbox APNs
   with the existing `aps-environment` entitlements) + physical Android; jest tests
   for the JS wrapper (payload parsing, completion adaptation); README with the
   integration contract; version + changelog.

Deliberately out of scope (per requirements — do not add): scheduled/local
reminders, action buttons/categories, badge management, grouping, rich media,
inbox/history.

Example-app test artifacts to be aware of: `example/android/app` uses
`applicationId com.obitrain.obiapp.dev.release` and a **copy of obiapp's
`google-services.json`** purely so FCM token fetch works; keep these out of any
public repo.

## Estimate

Remaining: **2.5–4 focused days** (Android receive/tap ≈ 1–1.5 d, obiapp
integration + QA ≈ 1 d, device pass + polish ≈ 0.5–1 d, buffer for FCM
killed-state edge cases).

## Verdict: finish it

- The riskiest pieces are already proven working end-to-end: raw APNs token on iOS,
  FCM token on Android, and — critically — async JS completion handlers driving OS
  completion blocks across the Nitro boundary, with deadline fallbacks.
- There is no maintained alternative under the constraints: wix is abandoned (and
  runs through legacy bridge interop, which RN is scheduled to remove — a forcing
  function, not a hypothetical), notifee is archived, expo-notifications is ruled
  out. Carrying the dead dependency means betting obiapp's push stack on interop
  shims and hand-maintained patches at every RN upgrade.
- The owned surface is small: ~250 lines of Swift, ~150 of Kotlin (plus the FCM
  service to come), ~130 of TS — sized to obiapp's actual usage, on the
  architecture obiapp already ships three modules with.
- Cost to finish (≈3 days) is comparable to what the next one or two RN upgrades
  would cost in wix patch archaeology, and it removes both existing patches.
