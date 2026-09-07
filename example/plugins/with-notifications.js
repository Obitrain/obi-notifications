const {
  withAppDelegate,
  withPodfile,
  withMainActivity,
} = require('expo/config-plugins');

const callbacks = `
  public override func application(
    _ application: UIApplication,
    didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
  ) {
    super.application(application, didRegisterForRemoteNotificationsWithDeviceToken: deviceToken)
    ObiNotificationsCenter.didRegisterForRemoteNotifications(deviceToken: deviceToken)
  }

  public override func application(
    _ application: UIApplication,
    didFailToRegisterForRemoteNotificationsWithError error: Error
  ) {
    super.application(application, didFailToRegisterForRemoteNotificationsWithError: error)
    ObiNotificationsCenter.didFailToRegisterForRemoteNotifications(error: error)
  }

  public override func application(
    _ application: UIApplication,
    didReceiveRemoteNotification userInfo: [AnyHashable: Any],
    fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
  ) {
    // The library owns completion and its timeout; do not complete twice through super.
    ObiNotificationsCenter.didReceiveRemoteNotification(
      userInfo: userInfo,
      completionHandler: completionHandler
    )
  }
`;

module.exports = (config) => {
  config = withMainActivity(config, (mod) => {
    const anchor = 'class MainActivity : ReactActivity() {';
    let contents = mod.modResults.contents;
    if (mod.modResults.language !== 'kt' || !contents.includes(anchor)) {
      throw new Error(
        'Unsupported Expo MainActivity: notification intent anchor missing'
      );
    }
    if (!contents.includes('import android.content.Intent')) {
      contents = contents.replace(
        'import android.os.Bundle',
        'import android.content.Intent\nimport android.os.Bundle'
      );
    }
    if (!contents.includes('override fun onNewIntent(intent: Intent)')) {
      contents = contents.replace(
        anchor,
        `${anchor}
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
  }
`
      );
    }
    mod.modResults.contents = contents;
    return mod;
  });
  config = withPodfile(config, (mod) => {
    const anchor = 'use_expo_modules!';
    const pod = "pod 'ObiNotificationsCore', :path => '../../ios/core'";
    if (!mod.modResults.contents.includes(anchor)) {
      throw new Error('Unsupported Expo Podfile: module setup anchor missing');
    }
    if (!mod.modResults.contents.includes(pod)) {
      mod.modResults.contents = mod.modResults.contents.replace(
        anchor,
        `${anchor}\n  ${pod}`
      );
    }
    return mod;
  });
  return withAppDelegate(config, (mod) => {
    const launch = 'let delegate = ReactNativeDelegate()';
    const end = '\n}\n\nclass ReactNativeDelegate';
    let contents = mod.modResults.contents;
    if (
      mod.modResults.language !== 'swift' ||
      !contents.includes(launch) ||
      !contents.includes(end)
    ) {
      throw new Error(
        'Unsupported Expo AppDelegate: notification setup anchors missing'
      );
    }
    if (!contents.includes('import ObiNotificationsCore')) {
      contents = contents.replace(
        'import React\n',
        'import React\nimport ObiNotificationsCore\n'
      );
    }
    if (!contents.includes('ObiNotificationsCenter.attach()')) {
      contents = contents.replace(
        launch,
        `ObiNotificationsCenter.attach()\n    ${launch}`
      );
    }
    if (
      !contents.includes(
        'ObiNotificationsCenter.didRegisterForRemoteNotifications'
      )
    ) {
      contents = contents.replace(end, `${callbacks}${end}`);
    }
    mod.modResults.contents = contents;
    return mod;
  });
};
