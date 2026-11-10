import assert from 'node:assert/strict';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, signInAnonymously, signOut } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator, doc, getDoc, setDoc, terminate } from 'firebase/firestore';

const projectId = 'demo-lvluplife';
assert.equal(process.env.GCLOUD_PROJECT, projectId, 'Use only the demo emulator project');
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:8080');
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, '127.0.0.1:9099');
const app = initializeApp({ projectId, apiKey: 'demo-key', appId: 'demo-app' });
const auth = getAuth(app);
connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
const db = getFirestore(app);
connectFirestoreEmulator(db, '127.0.0.1', 8080);

async function denied(action, label) {
  await assert.rejects(action, (error) => error.code === 'permission-denied', label);
  console.log(`PASS ${label}`);
}

try {
  const reference = doc(db, 'users', 'synthetic-account');
  await denied(() => getDoc(reference), 'unauthenticated client read denied');
  await denied(() => setDoc(reference, { synthetic: true }), 'unauthenticated client write denied');
  await signInAnonymously(auth);
  console.log('PASS Auth available on 9099; Firestore on 8080');
  await denied(() => getDoc(reference), 'authenticated client read denied');
  await denied(() => setDoc(reference, { synthetic: true }), 'authenticated client write denied');
  await signOut(auth);
} finally {
  await terminate(db);
  await deleteApp(app);
}
