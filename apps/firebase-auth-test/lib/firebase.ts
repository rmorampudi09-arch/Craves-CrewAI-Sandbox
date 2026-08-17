import { FirebaseApp, getApp, getApps, initializeApp } from "firebase/app";
import { Auth, getAuth } from "firebase/auth";

export type FirebaseBrowserConfig = {
  apiKey: string;
  authDomain: string;
  projectId: string;
  appId: string;
  storageBucket?: string;
  messagingSenderId?: string;
};

type FirebaseClient = {
  app: FirebaseApp;
  auth: Auth;
};

export function getFirebaseClient(config: FirebaseBrowserConfig): FirebaseClient {
  if (!config.apiKey || !config.authDomain || !config.projectId || !config.appId) {
    throw new Error("Firebase config is incomplete. Fill apiKey, authDomain, projectId, and appId.");
  }

  const app = getApps().length > 0 ? getApp() : initializeApp(config);
  return {
    app,
    auth: getAuth(app)
  };
}
