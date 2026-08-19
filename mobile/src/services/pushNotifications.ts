import { getToken } from 'firebase/messaging';
import { apiRequest } from './apiClient';
import { firebaseMessaging } from './firebase';

export const registerPushNotifications = async (accessToken: string): Promise<string | null> => {
  const messaging = firebaseMessaging();

  if (!messaging) {
    return null;
  }

  const pushToken = await getToken(messaging).catch(() => null);

  if (!pushToken) {
    return null;
  }

  await apiRequest('/notifications/devices', {
    method: 'POST',
    token: accessToken,
    body: {
      platform: 'react-native',
      provider: 'fcm',
      token: pushToken,
    },
  });

  return pushToken;
};
