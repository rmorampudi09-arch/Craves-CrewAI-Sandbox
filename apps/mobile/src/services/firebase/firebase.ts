import {mobileEnvironment} from '../../config/env';

export type FirebaseBootstrapState = {
  configured: boolean;
  projectId?: string;
};

export const getFirebaseBootstrapState = (): FirebaseBootstrapState => ({
  configured: Boolean(mobileEnvironment.firebaseProjectId),
  projectId: mobileEnvironment.firebaseProjectId,
});
