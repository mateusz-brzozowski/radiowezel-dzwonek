import { initializeApp } from "firebase/app";
import {
  browserSessionPersistence,
  getAuth,
  setPersistence,
} from "firebase/auth";
import { getDatabase } from "firebase/database";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  databaseURL: import.meta.env.VITE_FIREBASE_DATABASE_URL,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
};

export const isFirebaseConfigured = Boolean(
  firebaseConfig.apiKey &&
    firebaseConfig.databaseURL &&
    firebaseConfig.projectId &&
    firebaseConfig.apiKey !== "REPLACE_ME"
);

export const app = isFirebaseConfigured ? initializeApp(firebaseConfig) : null;
export const auth = app ? getAuth(app) : null;
export const db = app ? getDatabase(app) : null;

/** Session-only auth: closing the tab/browser ends the session. */
export async function hardenAuthSession() {
  if (!auth) return;
  await setPersistence(auth, browserSessionPersistence);
}

/** Wipe any insecure leftovers from earlier panel builds. */
export function clearInsecureBrowserSecrets() {
  try {
    localStorage.removeItem("alarmPin");
    localStorage.removeItem("deviceId");
    localStorage.removeItem("remoteToken");
  } catch (_) {
    /* ignore */
  }
}
