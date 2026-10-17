import { readFileSync } from 'node:fs';
import { before, after, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, collection, setDoc, getDoc, getDocs, updateDoc, deleteDoc, serverTimestamp, deleteField } from 'firebase/firestore';
import { ref, uploadBytes, getBytes, deleteObject, listAll } from 'firebase/storage';
let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-vidgravity',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8') },
    storage: { rules: readFileSync('../storage.rules', 'utf8') } });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); await env.clearStorage(); });
const profile = () => ({ displayName: 'Alice', updatedAt: serverTimestamp() });
test('profile CRUD is private; listing and unknown paths denied', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const bob = env.authenticatedContext('bob').firestore();
  const anon = env.unauthenticatedContext().firestore();
  await assertSucceeds(setDoc(doc(alice, 'users/alice'), profile()));
  await assertSucceeds(getDoc(doc(alice, 'users/alice')));
  for (const db of [bob, anon]) {
    await assertFails(getDoc(doc(db, 'users/alice')));
    await assertFails(setDoc(doc(db, 'users/alice'), profile()));
    await assertFails(deleteDoc(doc(db, 'users/alice')));
    await assertFails(getDocs(collection(db, 'users')));
  }
  await assertFails(getDocs(collection(alice, 'users')));
  await assertFails(setDoc(doc(alice, 'users/alice/private/x'), profile()));
  await assertFails(setDoc(doc(alice, 'licenses/alice'), { admin: true }));
  await assertSucceeds(deleteDoc(doc(alice, 'users/alice')));
});
test('profile create AND update enforce schema, types, bounds and server time', async () => {
  const db = env.authenticatedContext('alice').firestore();
  const target = doc(db, 'users/alice');
  for (const data of [ {}, { ...profile(), admin: true }, { ...profile(), displayName: 5 },
    { ...profile(), displayName: 'x'.repeat(81) }, { ...profile(), displayName: '   ' },
    { ...profile(), updatedAt: new Date(0) } ]) {
    await assertFails(setDoc(target, data));
  }
  await assertSucceeds(setDoc(target, profile()));
  for (const patch of [ { admin: true }, { displayName: 'x'.repeat(81) },
    { displayName: 1 }, { displayName: deleteField() }, { updatedAt: new Date(0) } ]) {
    await assertFails(updateDoc(target, patch));
  }
  await assertSucceeds(updateDoc(target, { displayName: 'New name', updatedAt: serverTimestamp() }));
});
test('storage isolates owners and forbids listing, nested and unknown paths', async () => {
  const alice = env.authenticatedContext('alice').storage();
  const path = 'users/alice/images/avatar';
  await assertSucceeds(uploadBytes(ref(alice, path), new Uint8Array([1]), { contentType: 'image/png' }));
  await assertSucceeds(getBytes(ref(alice, path)));
  for (const storage of [env.authenticatedContext('bob').storage(), env.unauthenticatedContext().storage()]) {
    await assertFails(getBytes(ref(storage, path)));
    await assertFails(uploadBytes(ref(storage, path), new Uint8Array([1]), { contentType: 'image/png' }));
    await assertFails(deleteObject(ref(storage, path)));
  }
  await assertFails(listAll(ref(alice, 'users/alice/images')));
  await assertFails(uploadBytes(ref(alice, path + '/nested'), new Uint8Array([1]), { contentType: 'image/png' }));
  await assertSucceeds(deleteObject(ref(alice, path)));
});
test('storage rejects empty, oversized and active content on create and overwrite', async () => {
  const storage = env.authenticatedContext('alice').storage();
  const target = ref(storage, 'users/alice/images/avatar');
  for (const existing of [false, true]) {
    if (existing) await assertSucceeds(uploadBytes(target, new Uint8Array([1]), { contentType: 'image/jpeg' }));
    for (const [size, type] of [[0, 'image/png'], [10 * 1024 * 1024 + 1, 'image/png'], [1, 'text/html'], [1, 'image/svg+xml']]) {
      await assertFails(uploadBytes(target, new Uint8Array(size), { contentType: type }));
    }
  }
});
