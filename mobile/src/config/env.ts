export type MobileEnv = {
  apiBaseUrl: string;
  firebaseApiKey: string;
  firebaseAuthDomain: string;
  firebaseProjectId: string;
  firebaseAppId: string;
  firebaseMessagingSenderId: string;
};

const getRequired = (key: string, fallback = ''): string => {
  const value = (globalThis as any)?.process?.env?.[key] ?? fallback;

  if (!value) {
    throw new Error(`Missing required mobile env: ${key}`);
  }

  return value;
};

export const mobileEnv: MobileEnv = {
  apiBaseUrl: getRequired('CRAVES_API_BASE_URL', 'https://api.example.craves.local'),
  firebaseApiKey: getRequired('CRAVES_FIREBASE_API_KEY', 'FIREBASE_API_KEY'),
  firebaseAuthDomain: getRequired('CRAVES_FIREBASE_AUTH_DOMAIN', 'example.firebaseapp.com'),
  firebaseProjectId: getRequired('CRAVES_FIREBASE_PROJECT_ID', 'craves-example'),
  firebaseAppId: getRequired('CRAVES_FIREBASE_APP_ID', '1:000000000000:ios:example'),
  firebaseMessagingSenderId: getRequired('CRAVES_FIREBASE_MESSAGING_SENDER_ID', '000000000000'),
};
