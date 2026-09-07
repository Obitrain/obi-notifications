import assert from 'node:assert/strict';
import { test } from 'node:test';
import plugin from '../with-notifications.js';

const delegate = `import React
class AppDelegate: ExpoAppDelegate {
  func launch() {
    let delegate = ReactNativeDelegate()
  }
}

class ReactNativeDelegate: ExpoReactNativeFactoryDelegate {}
`;

async function apply(mod, contents, language = 'swift') {
  const config = plugin({ name: 'Example', slug: 'example' });
  const platform = mod === 'mainActivity' ? 'android' : 'ios';
  const result = await config.mods[platform][mod]({
    ...config,
    modResults: { contents, language },
    modRequest: { platform, modName: mod },
  });
  return result.modResults.contents;
}

for (const [name, contents, language] of [
  ['changed Swift template', 'class AppDelegate {}', 'swift'],
  ['Objective-C template', delegate, 'objc'],
]) {
  test(`rejects ${name} instead of silently losing native setup`, async () => {
    await assert.rejects(
      apply('appDelegate', contents, language),
      /Unsupported Expo AppDelegate/
    );
  });
}

test('preserves APNs registration and notification callbacks without duplication', async () => {
  const result = await apply('appDelegate', delegate);
  for (const call of [
    'attach',
    'didRegisterForRemoteNotifications',
    'didFailToRegisterForRemoteNotifications',
    'didReceiveRemoteNotification',
  ]) {
    assert.ok(result.includes(`ObiNotificationsCenter.${call}(`));
  }
  assert.match(result, /import ObiNotificationsCore/);
  assert.match(
    result,
    /super\.application\(application, didRegisterForRemoteNotificationsWithDeviceToken:/
  );
  assert.match(
    result,
    /super\.application\(application, didFailToRegisterForRemoteNotificationsWithError:/
  );
  assert.doesNotMatch(
    result,
    /super\.application\(application, didReceiveRemoteNotification:/
  );
  assert.equal(await apply('appDelegate', result), result);
});

test('adds the local core pod once and rejects incompatible Podfiles', async () => {
  const result = await apply(
    'podfile',
    "target 'Example' do\n  use_expo_modules!\nend",
    'ruby'
  );
  assert.match(
    result,
    /pod 'ObiNotificationsCore', :path => '\.\.\/\.\.\/ios\/core'/
  );
  assert.equal(await apply('podfile', result, 'ruby'), result);
  await assert.rejects(
    apply('podfile', 'target do end', 'ruby'),
    /Unsupported Expo Podfile/
  );
});

test('keeps FCM tap extras after activity relaunch and repeated generation', async () => {
  const input =
    'import android.os.Bundle\nclass MainActivity : ReactActivity() {\n}';
  const result = await apply('mainActivity', input, 'kt');
  assert.match(result, /import android.content.Intent/);
  assert.match(result, /super.onNewIntent\(intent\)\s+setIntent\(intent\)/);
  assert.equal(await apply('mainActivity', result, 'kt'), result);
  await assert.rejects(
    apply('mainActivity', 'class MainActivity {}', 'kt'),
    /Unsupported Expo MainActivity/
  );
});
