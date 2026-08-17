"use client";

import { getApp, getApps, initializeApp, type FirebaseApp } from "firebase/app";
import { getAuth, type Auth } from "firebase/auth";

export type FirebaseBrowserClient = { app: FirebaseApp; auth: Auth };

function requiredPublicValue(name: string, value: string | undefined): string {
  if (!value?.trim()) throw new Error(`${name} is not configured for the customer website.`);
  return value.trim();
}

export function getFirebaseBrowserClient(): FirebaseBrowserClient {
  const config = {
    apiKey: requiredPublicValue("NEXT_PUBLIC_FIREBASE_API_KEY", process.env.NEXT_PUBLIC_FIREBASE_API_KEY),
    authDomain: requiredPublicValue("NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN", process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN),
    projectId: requiredPublicValue("NEXT_PUBLIC_FIREBASE_PROJECT_ID", process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID),
    appId: requiredPublicValue("NEXT_PUBLIC_FIREBASE_APP_ID", process.env.NEXT_PUBLIC_FIREBASE_APP_ID),
    messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID?.trim(),
    storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET?.trim(),
  };
  const app = getApps().length > 0 ? getApp() : initializeApp(config);
  return { app, auth: getAuth(app) };
}
