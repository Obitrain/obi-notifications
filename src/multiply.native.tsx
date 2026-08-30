import { NitroModules } from 'react-native-nitro-modules';
import type { ReactNativeNotifications } from './ReactNativeNotifications.nitro';

const ReactNativeNotificationsHybridObject =
  NitroModules.createHybridObject<ReactNativeNotifications>('ReactNativeNotifications');

export function multiply(a: number, b: number): number {
  return ReactNativeNotificationsHybridObject.multiply(a, b);
}
