import type { HybridObject } from 'react-native-nitro-modules';

/** Mirrors iOS UIBackgroundFetchResult. */
export type BackgroundFetchResult = 'newData' | 'noData' | 'failed';

/** How iOS should present a notification received while the app is foregrounded. */
export interface NotificationCompletion {
  alert?: boolean;
  sound?: boolean;
  badge?: boolean;
}

export interface RegistrationErrorEvent {
  code: string;
  domain: string;
  localizedDescription: string;
}

export interface NotificationChannelConfig {
  channelId: string;
  name: string;
  description?: string;
  /** android.app.NotificationManager importance (0-5). */
  importance?: number;
  enableLights?: boolean;
  enableVibration?: boolean;
  showBadge?: boolean;
  vibrationPattern?: number[];
}

/**
 * Notification payloads cross the Nitro boundary as JSON strings
 * (`payloadJson`); the JS wrapper parses/stringifies at the edge.
 */
export interface ReactNativeNotifications extends HybridObject<{
  ios: 'swift';
  android: 'kotlin';
}> {
  /**
   * iOS: request notification permission, then register with APNs.
   * Resolves once the permission dialog completed (not when the token arrives).
   * Android: fetch the current FCM token with bounded retries.
   * The token itself is delivered through onTokenReceived.
   */
  registerRemoteNotifications(): Promise<void>;

  /** JSON payload of the notification that cold-started the app, if any. */
  getInitialNotification(): Promise<string | undefined>;

  /** Android only: render a notification while the app is foregrounded. */
  postLocalNotification(payloadJson: string): void;

  /** Android only: create/update a notification channel. No-op on iOS. */
  setNotificationChannel(channel: NotificationChannelConfig): void;

  /** Raw APNs device token (hex) on iOS, FCM registration token on Android. */
  onTokenReceived(callback: (deviceToken: string) => void): void;

  onRegistrationFailed(callback: (error: RegistrationErrorEvent) => void): void;

  /**
   * Notification received while foregrounded. Native awaits the returned
   * promise and forwards the presentation options to the OS completion
   * handler (iOS willPresent). A native-side deadline applies.
   */
  onNotificationReceivedForeground(
    callback: (payloadJson: string) => Promise<NotificationCompletion>
  ): void;

  /**
   * User tapped a notification. Native awaits the returned promise before
   * calling the OS completion handler.
   */
  onNotificationOpened(callback: (payloadJson: string) => Promise<void>): void;

  /**
   * iOS: remote notification delivered while backgrounded
   * (didReceiveRemoteNotification). Native awaits the returned fetch result.
   */
  onNotificationReceivedBackground(
    callback: (payloadJson: string) => Promise<BackgroundFetchResult>
  ): void;
}
