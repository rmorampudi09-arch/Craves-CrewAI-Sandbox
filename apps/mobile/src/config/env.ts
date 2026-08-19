export type MobileEnvironment = {
  apiBaseUrl: string;
  firebaseProjectId?: string;
  environmentName: string;
};

const readString = (value: string | undefined, fallback: string): string => {
  if (typeof value === 'string' && value.trim().length > 0) {
    return value.trim();
  }

  return fallback;
};

export const mobileEnvironment: MobileEnvironment = {
  apiBaseUrl: readString(process.env.CRAVES_API_BASE_URL, 'http://localhost:8080'),
  firebaseProjectId: process.env.CRAVES_FIREBASE_PROJECT_ID,
  environmentName: readString(process.env.CRAVES_ENV_NAME, 'local'),
};
