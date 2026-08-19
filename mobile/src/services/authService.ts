import { User, signInWithCustomToken, signOut } from 'firebase/auth';
import { apiRequest } from './apiClient';
import { firebaseAuth } from './firebase';

export type BackendTokenExchangeResponse = {
  accessToken: string;
  refreshToken?: string;
  expiresIn?: number;
  roles?: string[];
  userId?: string;
};

export const signInWithFirebaseCustomToken = async (customToken: string): Promise<User> => {
  const credential = await signInWithCustomToken(firebaseAuth(), customToken);
  return credential.user;
};

export const exchangeFirebaseIdToken = async (
  firebaseIdToken: string,
): Promise<BackendTokenExchangeResponse> => {
  return apiRequest<BackendTokenExchangeResponse>('/auth/token/exchange', {
    method: 'POST',
    body: {
      provider: 'firebase',
      idToken: firebaseIdToken,
    },
  });
};

export const loadAuthenticatedSession = async (): Promise<BackendTokenExchangeResponse> => {
  const currentUser = firebaseAuth().currentUser;

  if (!currentUser) {
    throw new Error('No Firebase user is currently signed in');
  }

  const firebaseIdToken = await currentUser.getIdToken();
  return exchangeFirebaseIdToken(firebaseIdToken);
};

export const logoutMobileSession = async (): Promise<void> => {
  try {
    await apiRequest('/auth/logout', { method: 'POST' });
  } finally {
    await signOut(firebaseAuth());
  }
};
