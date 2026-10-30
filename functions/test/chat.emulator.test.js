const { test, after } = require('node:test');
const assert = require('node:assert/strict');
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099';
const { initializeApp, deleteApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const app = initializeApp({ projectId: 'fashion2hand' }, `chat-${Date.now()}`);
const db = getFirestore(app);
after(() => deleteApp(app));
const root = 'http://127.0.0.1:8080/v1/projects/fashion2hand/databases/(default)/documents/';
async function post(url, data, token) {
  const result = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(data), signal: AbortSignal.timeout(60000) });
  return { status: result.status, body: await result.json() };
}
async function account(name) {
  const result = await post('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator', { email: `${name}-${Date.now()}-${Math.random()}@example.com`, password: 'Test-only-123!', returnSecureToken: true });
  assert.equal(result.status, 200);
  const user = { uid: result.body.localId, token: result.body.idToken };
  await db.doc(`users/${user.uid}`).set({ uid: user.uid, name, accountStatus: 'ACTIVE', role: 'USER' });
  return user;
}
async function call(name, data, user, status = 200) {
  const result = await post(`http://127.0.0.1:5001/fashion2hand/us-central1/${name}`, { data }, user?.token);
  assert.equal(result.status, status, JSON.stringify(result)); return result.body.result;
}
async function read(path, user) {
  return fetch(root + path, { headers: { authorization: `Bearer ${user.token}` }, signal: AbortSignal.timeout(30000) });
}
async function fixture() {
  const seller = await account('chat-seller'), buyer = await account('chat-buyer'), outsider = await account('chat-outsider');
  const post = db.collection('posts').doc(); await post.set({ ownerId: seller.uid, type: 'SALE', status: 'ACTIVE', price: 120000 });
  const { chatId } = await call('createChat', { otherUserId: seller.uid, postId: post.id }, buyer);
  return { seller, buyer, outsider, postId: post.id, chatId };
}

test('both roles open the same product chat; strangers cannot read or send', async () => {
  const f = await fixture();
  assert.equal((await call('createChat', { otherUserId: f.buyer.uid, postId: f.postId }, f.seller)).chatId, f.chatId);
  await call('createChat', { otherUserId: f.buyer.uid, postId: f.postId }, f.outsider, 400);
  await call('sendMessage', { chatId: f.chatId, text: 'forbidden' }, f.outsider, 403);
  await call('markChatRead', { chatId: f.chatId }, f.outsider, 403);
  assert.equal((await read(`chats/${f.chatId}`, f.outsider)).status, 403);
  const message = await call('sendMessage', { chatId: f.chatId, text: 'Xin chao', clientMessageId: 'hello' }, f.buyer);
  for (const user of [f.buyer, f.seller]) assert.equal((await read(`chats/${f.chatId}/messages/${message.messageId}`, user)).status, 200);
  assert.equal((await read(`chats/${f.chatId}/messages/${message.messageId}`, f.outsider)).status, 403);
  const direct = await fetch(root + `chats/${f.chatId}/messages/forged`, { method: 'PATCH', headers: { authorization: `Bearer ${f.buyer.token}`, 'content-type': 'application/json' }, body: JSON.stringify({ fields: { text: { stringValue: 'forged' } } }) });
  assert.equal(direct.status, 403);
});
test('one chat per pair: other posts and no-post chats reuse the same room', async () => {
  const f = await fixture();
  const second = db.collection('posts').doc(); await second.set({ ownerId: f.seller.uid, type: 'SALE', status: 'ACTIVE', price: 90000 });
  assert.equal((await call('createChat', { otherUserId: f.seller.uid, postId: second.id }, f.buyer)).chatId, f.chatId);
  assert.equal((await call('createChat', { otherUserId: f.buyer.uid }, f.seller)).chatId, f.chatId);
  assert.equal((await db.doc(`chats/${f.chatId}`).get()).get('postId'), second.id);
  const chats = await db.collection('chats').where('participantIds', 'array-contains', f.buyer.uid).get();
  assert.equal(chats.docs.filter(doc => doc.get('participantIds').includes(f.seller.uid)).length, 1);
});
test('old per-post rooms are merged into one room with every message and unread count', async () => {
  const f = await fixture();
  await call('sendMessage', { chatId: f.chatId, text: 'Tin trong phong chuan', clientMessageId: 'pair' }, f.buyer);
  const legacy = db.collection('chats').doc();
  await legacy.set({ participantIds: [f.buyer.uid, f.seller.uid].sort(), postId: f.postId, lastMessage: 'Tin cu', lastMessageAt: Timestamp.now(), updatedAt: Timestamp.now(), unreadCounts: { [f.seller.uid]: 2, [f.buyer.uid]: 0 } });
  await legacy.collection('messages').doc('old-1').set({ senderId: f.buyer.uid, text: 'Tin cu 1', read: false, createdAt: Timestamp.now() });
  await legacy.collection('messages').doc('old-2').set({ senderId: f.buyer.uid, text: 'Tin cu', read: false, createdAt: Timestamp.now() });
  assert.equal((await call('mergeMyChats', {}, f.seller)).merged, 1);
  assert.equal((await call('mergeMyChats', {}, f.buyer)).merged, 0);
  const room = await db.doc(`chats/${f.chatId}`).get();
  assert.equal((await db.collection(`chats/${f.chatId}/messages`).get()).size, 3);
  assert.equal(room.get(`unreadCounts.${f.seller.uid}`), 3);
  assert.equal(room.get('lastMessage'), 'Tin cu');
  assert.equal((await legacy.get()).get('mergedInto'), f.chatId);
  const sent = await call('sendMessage', { chatId: legacy.id, text: 'Gui vao phong cu', clientMessageId: 'redirect' }, f.buyer);
  assert.equal((await db.doc(`chats/${f.chatId}/messages/${sent.messageId}`).get()).get('text'), 'Gui vao phong cu');
});
test('concurrent retries write one message, one notification and one unread increment', async () => {
  const f = await fixture(); const data = { chatId: f.chatId, text: 'Tin nhan duy nhat', clientMessageId: 'retry-key' };
  const results = await Promise.all([call('sendMessage', data, f.buyer), call('sendMessage', data, f.buyer)]);
  assert.equal(results[0].messageId, results[1].messageId);
  assert.equal((await db.collection(`chats/${f.chatId}/messages`).get()).size, 1);
  assert.equal((await db.doc(`chats/${f.chatId}`).get()).get(`unreadCounts.${f.seller.uid}`), 1);
  assert.equal((await db.collection(`users/${f.seller.uid}/notifications`).where('chatId', '==', f.chatId).get()).size, 1);
  await call('sendMessage', { ...data, text: 'changed payload' }, f.buyer, 409);
  const before = (await db.doc(`chats/${f.chatId}`).get()).get('updatedAt');
  await call('markChatRead', { chatId: f.chatId }, f.seller);
  assert.equal((await db.doc(`chats/${f.chatId}/messages/${results[0].messageId}`).get()).get('read'), true);
  assert.equal((await db.doc(`chats/${f.chatId}`).get()).get(`unreadCounts.${f.seller.uid}`), 0);
  assert.ok(before.isEqual((await db.doc(`chats/${f.chatId}`).get()).get('updatedAt')));
});

test('history cursors include every message once when timestamps tie and a new tail arrives', async () => {
  const f = await fixture(); const ref = db.collection(`chats/${f.chatId}/messages`);
  const timestamp = Timestamp.now(); const batch = db.batch();
  for (let i = 0; i < 85; i++) batch.set(ref.doc(String(i).padStart(3, '0')), { senderId: f.buyer.uid, text: `History ${i}`, read: false, createdAt: timestamp });
  await batch.commit();
  const query = ref.orderBy('createdAt', 'desc').orderBy('__name__', 'desc');
  const first = await query.limit(40).get(); assert.equal(first.size, 40);
  const newer = await call('sendMessage', { chatId: f.chatId, text: 'Live tail', clientMessageId: 'tail' }, f.seller);
  const second = await query.startAfter(first.docs.at(-1)).limit(40).get();
  const third = await query.startAfter(second.docs.at(-1)).limit(40).get();
  assert.equal(second.size, 40); assert.equal(third.size, 5);
  const ids = new Set([...first.docs, ...second.docs, ...third.docs].map(document => document.id));
  assert.equal(ids.size, 85); assert.equal(ids.has(newer.messageId), false);
  assert.equal((await read(`chats/${f.chatId}/messages/000`, f.seller)).status, 200);
});