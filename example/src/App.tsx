import { useCallback, useEffect, useState } from 'react';
import {
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {
  NotificationBackgroundFetchResult,
  Notifications,
} from '@obitrain/react-native-notifications';

export default function App() {
  const [token, setToken] = useState<string>();
  const [error, setError] = useState<string>();
  const [log, setLog] = useState<string[]>([]);

  const appendLog = useCallback((line: string) => {
    console.log(`[obi-notifications] ${line}`);
    setLog((prev) => [...prev, line]);
  }, []);

  useEffect(() => {
    const events = Notifications.events();
    events.registerRemoteNotificationsRegistered(({ deviceToken }) => {
      setToken(deviceToken);
      appendLog(`token: ${deviceToken}`);
    });
    events.registerRemoteNotificationsRegistrationFailed((e) => {
      setError(`${e.domain} ${e.code}: ${e.localizedDescription}`);
      appendLog(`registration failed: ${JSON.stringify(e)}`);
    });
    events.registerNotificationReceivedForeground(
      (notification, completion) => {
        appendLog(`foreground: ${JSON.stringify(notification.payload)}`);
        if (Platform.OS === 'android') {
          Notifications.postLocalNotification(notification.payload);
        }
        completion({ alert: true, sound: true, badge: false });
      }
    );
    events.registerNotificationOpened((notification, completion) => {
      appendLog(`opened: ${JSON.stringify(notification.payload)}`);
      completion();
    });
    events.registerNotificationReceivedBackground(
      (notification, completion) => {
        appendLog(`background: ${JSON.stringify(notification.payload)}`);
        completion(NotificationBackgroundFetchResult.NO_DATA);
      }
    );

    if (Platform.OS === 'android') {
      Notifications.setNotificationChannel({
        channelId: 'default',
        name: 'Obitrain',
        description: 'Default Obitrain channel',
        importance: 4,
        enableLights: true,
        enableVibration: true,
        showBadge: true,
        vibrationPattern: [200, 1000, 500, 1000, 500],
      });
    }

    Notifications.getInitialNotification().then((notification) => {
      appendLog(`initial notification: ${JSON.stringify(notification)}`);
    });
  }, [appendLog]);

  const register = useCallback(async () => {
    appendLog('registerRemoteNotifications()...');
    await Notifications.registerRemoteNotifications();
    appendLog('registerRemoteNotifications() resolved');
  }, [appendLog]);

  const postLocal = useCallback(() => {
    appendLog('postLocalNotification()');
    Notifications.postLocalNotification({
      title: 'Local notification',
      body: 'Tap me',
      kind: 'local',
      id: String(Date.now()),
    });
  }, [appendLog]);

  return (
    <View style={styles.container}>
      <TouchableOpacity
        style={styles.button}
        onPress={register}
        testID="register-btn"
      >
        <Text style={styles.buttonLabel}>Register for notifications</Text>
      </TouchableOpacity>
      {Platform.OS === 'android' && (
        <TouchableOpacity
          style={[styles.button, styles.secondary]}
          onPress={postLocal}
          testID="post-local-btn"
        >
          <Text style={styles.buttonLabel}>Post local notification</Text>
        </TouchableOpacity>
      )}
      <Text style={styles.label}>Token:</Text>
      <Text testID="token" style={styles.token} selectable>
        {token ?? '(none)'}
      </Text>
      {error != null && <Text style={styles.error}>{error}</Text>}
      <Text style={styles.label}>Log:</Text>
      <ScrollView style={styles.log}>
        {log.map((line, i) => (
          <Text key={i} style={styles.logLine}>
            {line}
          </Text>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 80,
    paddingHorizontal: 16,
  },
  button: {
    backgroundColor: '#2563eb',
    borderRadius: 8,
    padding: 14,
    alignItems: 'center',
  },
  secondary: {
    marginTop: 8,
    backgroundColor: '#4b5563',
  },
  buttonLabel: {
    color: 'white',
    fontWeight: '600',
  },
  label: {
    marginTop: 16,
    fontWeight: '700',
  },
  token: {
    marginTop: 4,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    fontSize: 12,
  },
  error: {
    marginTop: 8,
    color: '#dc2626',
  },
  log: {
    marginTop: 4,
    flex: 1,
  },
  logLine: {
    fontSize: 11,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    marginBottom: 2,
  },
});
