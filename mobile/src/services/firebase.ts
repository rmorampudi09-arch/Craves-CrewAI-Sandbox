import { initializeApp, getApps, FirebaseApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getMessaging } from 'firebase/messaging';
import { mobileEnv } from '../config/env';

let appInstance: FirebaseApp | null = null;

export const getFirebaseApp = (): FirebaseApp => {
  if (appInstance) {
    return appInstance;
  }

  appInstance = getApps()[0] ?? initializeApp({
    apiKey: mobileEnv.firebaseApiKey,
    authDomain: mobileEnv.firebaseAuthDomain,
    projectId: mobileEnv.firebaseProjectId,
    appId: mobileEnv.firebaseAppId,
    messagingSenderId: mobileEnv.firebaseMessagingSenderId,
  });

  return appInstance;
};

export const firebaseAuth = () => getAuth(getFirebaseApp());

export const firebaseMessaging = () => {
  try {
    return getMessaging(getFirebaseApp());
  } catch {
    return null;
  }
};
