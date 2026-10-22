import { initializeApp, type FirebaseOptions } from 'firebase/app'
import { connectAuthEmulator, getAuth, type Auth } from 'firebase/auth'
import {
  connectFirestoreEmulator, initializeFirestore, persistentLocalCache, persistentMultipleTabManager, type Firestore,
} from 'firebase/firestore'

export interface FirebaseHandles {
  auth: Auth
  db: Firestore
  emulator: boolean
}

const EMULATOR = import.meta.env.VITE_USE_EMULATOR === 'true'

// Firebase Hosting serves the project's public web config at this address,
// so no keys live in the repository.
async function hostingConfig(): Promise<FirebaseOptions> {
  const res = await fetch('/__/firebase/init.json')
  if (!res.ok) throw new Error('This page must be opened from its Firebase Hosting address.')
  const config: FirebaseOptions = await res.json()
  // Signing in through the site's own domain avoids third-party-cookie trouble.
  if (/\.(web\.app|firebaseapp\.com)$/.test(location.hostname)) config.authDomain = location.hostname
  return config
}

async function init(): Promise<FirebaseHandles> {
  const config: FirebaseOptions = EMULATOR
    ? { apiKey: 'demo-key', authDomain: 'demo-fintrack.firebaseapp.com', projectId: 'demo-fintrack', appId: 'demo-app' }
    : await hostingConfig()
  const app = initializeApp(config)
  const auth = getAuth(app)
  // The browser keeps a copy of the data, so the app opens instantly and works offline.
  const db = initializeFirestore(app, { localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }) })
  if (EMULATOR) {
    connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true })
    connectFirestoreEmulator(db, '127.0.0.1', 8080)
  }
  return { auth, db, emulator: EMULATOR }
}

let handles: Promise<FirebaseHandles> | null = null

/** Initialises Firebase once, however many times it is asked for. */
export function initFirebase(): Promise<FirebaseHandles> {
  handles ??= init()
  return handles
}
