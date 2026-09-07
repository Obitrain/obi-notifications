# Expo development example

This example uses Expo SDK 57 and a development build containing the local native library. Expo Go cannot load it.
Run these commands from the repository root:

```sh
yarn install --immutable
yarn prepare
yarn example prebuild --clean
yarn example android
# Or: yarn example ios
```

Use `yarn example start` for subsequent JavaScript development. Rebuild after changing native code.
The example's `ios/` and `android/` directories are generated and ignored. Keep native configuration in app config and config plugins; `prebuild --clean` replaces generated projects.

Validate with `yarn lint`, `yarn typecheck`, `yarn prepare`, `yarn example export`, and `cd example && npx expo-doctor@1.20.4`.
After prebuild, `yarn example build:android` builds an arm64 debug APK and `yarn example build:ios` compiles an unsigned simulator app. CI runs both native builds.

Before prebuild, place your Firebase configuration at `example/google-services.json`, or set `GOOGLE_SERVICES_JSON` to its path relative to `example/`. It must contain the Android client `com.obitrain.obiapp.dev.release`.
For compilation only, use `GOOGLE_SERVICES_JSON=./google-services.ci.json`; the fixture cannot receive real pushes.
The config plugin preserves the native APNs callbacks and local `ObiNotificationsCore` pod. Real APNs testing requires a signed device build with the matching push entitlement and provisioning profile.

Use **Test local notification** to test delivery without APNs signing. On iOS it requests notification permission and schedules a notification after one second; foreground/open events appear in the log. **Register for remote push** remains separate and requires valid signing for APNs registration.
