import { NitroModules } from 'react-native-nitro-modules';
import type {
  BackgroundFetchResult,
  NotificationChannelConfig,
  NotificationCompletion,
  ReactNativeNotifications,
  RegistrationErrorEvent,
} from './ReactNativeNotifications.nitro';

const native = NitroModules.createHybridObject<ReactNativeNotifications>(
  'ReactNativeNotifications'
);

export interface Notification {
  /** Raw notification payload (APNs userInfo on iOS, FCM message on Android). */
  payload: any;
}

export interface Registered {
  deviceToken: string;
}

export type RegistrationError = RegistrationErrorEvent;
export type { NotificationCompletion };

export const NotificationBackgroundFetchResult = {
  NEW_DATA: 'newData',
  NO_DATA: 'noData',
  RESULT_FAILED: 'failed',
} as const satisfies Record<string, BackgroundFetchResult>;

export type NotificationBackgroundFetchResult = BackgroundFetchResult;

const parseNotification = (payloadJson: string): Notification => {
  let payload: any = {};
  try {
    payload = JSON.parse(payloadJson);
  } catch {
    // keep empty payload for malformed native JSON
  }
  return { payload };
};

const events = {
  registerRemoteNotificationsRegistered(cb: (event: Registered) => void) {
    native.onTokenReceived((deviceToken) => cb({ deviceToken }));
  },

  registerRemoteNotificationsRegistrationFailed(
    cb: (event: RegistrationError) => void
  ) {
    native.onRegistrationFailed(cb);
  },

  registerNotificationReceivedForeground(
    cb: (
      notification: Notification,
      completion: (response: NotificationCompletion) => void
    ) => void
  ) {
    native.onNotificationReceivedForeground(
      (payloadJson) =>
        new Promise<NotificationCompletion>((resolve) => {
          cb(parseNotification(payloadJson), resolve);
        })
    );
  },

  registerNotificationOpened(
    cb: (notification: Notification, completion: () => void) => void
  ) {
    native.onNotificationOpened(
      (payloadJson) =>
        new Promise<void>((resolve) => {
          cb(parseNotification(payloadJson), resolve);
        })
    );
  },

  registerNotificationReceivedBackground(
    cb: (
      notification: Notification,
      completion: (response: NotificationBackgroundFetchResult) => void
    ) => void
  ) {
    native.onNotificationReceivedBackground(
      (payloadJson) =>
        new Promise<BackgroundFetchResult>((resolve) => {
          cb(parseNotification(payloadJson), resolve);
        })
    );
  },
};

export const Notifications = {
  /**
   * iOS: request permission + register with APNs.
   * Android: register the Firebase Installation ID with bounded retries.
   * Registration identifiers arrive through registerRemoteNotificationsRegistered.
   */
  registerRemoteNotifications(): Promise<void> {
    return native.registerRemoteNotifications();
  },

  async getInitialNotification(): Promise<Notification | undefined> {
    const payloadJson = await native.getInitialNotification();

    return payloadJson == null ? undefined : parseNotification(payloadJson);
  },

  /** Android only: render a notification while the app is foregrounded. */
  postLocalNotification(payload: any): void {
    native.postLocalNotification(JSON.stringify(payload ?? {}));
  },

  /** Android only: create/update a notification channel. */
  setNotificationChannel(channel: NotificationChannelConfig): void {
    native.setNotificationChannel(channel);
  },

  events() {
    return events;
  },
};

export type { NotificationChannelConfig };
