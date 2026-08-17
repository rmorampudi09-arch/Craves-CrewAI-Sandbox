import { FirebaseApp, getApps, initializeApp } from "firebase/app";
import { Auth, getAuth } from "firebase/auth";

export type FirebaseBrowserConfig = {
  apiKey: string;
  authDomain: string;
  projectId: string;
  appId: string;
  storageBucket?: string;
  messagingSenderId?: string;
};

export function getDefaultFirebaseConfig(): FirebaseBrowserConfig {
  return {
    apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY || "",
    authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN || "",
    projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID || "",
    appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID || "",
    storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET || "",
    messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID || ""
  };
}

export function isFirebaseConfigReady(config: FirebaseBrowserConfig): boolean {
  return Boolean(config.apiKey && config.authDomain && config.projectId && config.appId);
}

export function getFirebaseClient(config: FirebaseBrowserConfig): { app: FirebaseApp; auth: Auth } {
  if (!isFirebaseConfigReady(config)) {
    throw new Error("Firebase Web Config is incomplete. apiKey, authDomain, projectId, and appId are required.");
  }

  const app = getApps().length > 0 ? getApps()[0] : initializeApp(config);
  return {
    app,
    auth: getAuth(app)
  };
}
