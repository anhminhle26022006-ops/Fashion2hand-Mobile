const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");

// Explicit local endpoints: this suite never connects to production.
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const project = "fashion2hand";
const app = initializeApp({ projectId: project }, `marketplace-tests-${Date.now()}`);
const db = getFirestore(app);
const authUrl = "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/";
const docUrl = `http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents/`;
after(() => deleteApp(app));

async function post(url, data, token) {
  const response = await fetch(url, { method: "POST", headers: {
    "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {})
  }, body: JSON.stringify(data), signal: AbortSignal.timeout(30000) });
  return { status: response.status, body: await response.json() };
}
async function account(label) {
  const response = await post(authUrl + "accounts:signUp?key=emulator", {
    email: `${label}-${Date.now()}-${Math.random().toString(16).slice(2)}@example.com`, password: "Test-only-123!", returnSecureToken: true
  });
  assert.equal(response.status, 200, JSON.stringify(response.body));
  const { localId: uid, idToken: token, email } = response.body;
  await db.doc(`users/${uid}`).set({ uid, name: label, email, role: "USER", accountStatus: "ACTIVE",
    bio: "Giới thiệu công khai", region: "TP.HCM", photoUrl: "", address: "PRIVATE ADDRESS", phone: "PRIVATE PHONE" });
  return { uid, token, email };
}
async function callable(name, data, token) {
  return post(`http://127.0.0.1:5001/${project}/us-central1/${name}`, { data }, token);
}
function fields(data) {
  return Object.fromEntries(Object.entries(data).map(([key, value]) => [key,
    typeof value === "number" ? { integerValue: String(value) } : { stringValue: value }]));
}
async function patchDoc(path, data, token) {
  const response = await fetch(docUrl + path + "?" + Object.keys(data).map(key => `updateMask.fieldPaths=${key}`).join("&"), {
    method: "PATCH", headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
    body: JSON.stringify({ fields: fields(data) }), signal: AbortSignal.timeout(30000)
  });
  return { status: response.status, body: await response.json() };
}

test("seller profile exposes only public fields and enforces account status", async () => {
  const seller = await account("seller"); const viewer = await account("viewer");
  const profile = await callable("getSellerProfile", { sellerId: seller.uid }, viewer.token);
  assert.equal(profile.status, 200, JSON.stringify(profile.body));
  assert.deepEqual(Object.keys(profile.body.result).sort(), ["uid", "name", "photoUrl", "region", "bio"].sort());
  assert.equal(profile.body.result.name, "seller");
  const privateRead = await fetch(docUrl + `users/${seller.uid}`, { headers: { authorization: `Bearer ${viewer.token}` } });
  assert.equal(privateRead.status, 403);
  assert.equal((await callable("getSellerProfile", { sellerId: seller.uid })).body.error.status, "UNAUTHENTICATED");
  assert.equal((await callable("getSellerProfile", { sellerId: "../private" }, viewer.token)).body.error.status, "INVALID_ARGUMENT");
  await db.doc(`users/${seller.uid}`).update({ accountStatus: "LOCKED" });
  assert.equal((await callable("getSellerProfile", { sellerId: seller.uid }, viewer.token)).body.error.status, "NOT_FOUND");
});

test("SMS rejects wrong OTP, links correct OTP to existing UID, and sync ignores forged flags", async () => {
  const user = await account("phone");
  const phone = "+849" + String(Date.now()).slice(-8);
  const sent = await post(authUrl + "accounts:sendVerificationCode?key=emulator", { phoneNumber: phone });
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  const codes = await (await fetch(`http://127.0.0.1:9099/emulator/v1/projects/${project}/verificationCodes`)).json();
  const code = codes.verificationCodes.find(item => item.phoneNumber === phone).code;
  const bad = await post(authUrl + "accounts:signInWithPhoneNumber?key=emulator", {
    idToken: user.token, sessionInfo: sent.body.sessionInfo, code: code === "000000" ? "111111" : "000000"
  });
  assert.notEqual(bad.status, 200);
  const linked = await post(authUrl + "accounts:signInWithPhoneNumber?key=emulator", {
    idToken: user.token, sessionInfo: sent.body.sessionInfo, code
  });
  assert.equal(linked.status, 200, JSON.stringify(linked.body));
  assert.equal(linked.body.localId, user.uid);
  const sync = await callable("syncAccountContact", { phone: "+10000000000", emailVerified: true }, linked.body.idToken);
  assert.equal(sync.status, 200, JSON.stringify(sync.body));
  assert.equal(sync.body.result.phone, phone);
  assert.equal(sync.body.result.phoneVerified, true);
  assert.equal(sync.body.result.emailVerified, false);
});

test("email stays unchanged until Firebase verification link is consumed", async () => {
  const user = await account("email");
  const newEmail = `changed-${Date.now()}@example.com`;
  const sent = await post(authUrl + "accounts:sendOobCode?key=emulator", {
    requestType: "VERIFY_AND_CHANGE_EMAIL", idToken: user.token, newEmail
  });
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  const before = await post(authUrl + "accounts:lookup?key=emulator", { idToken: user.token });
  assert.equal(before.body.users[0].email, user.email);
  const codes = await (await fetch(`http://127.0.0.1:9099/emulator/v1/projects/${project}/oobCodes`)).json();
  const action = codes.oobCodes.find(item => item.newEmail === newEmail);
  assert.ok(action, "Verification action must target the new email");
  const applied = await post(authUrl + "accounts:update?key=emulator", { oobCode: action.oobCode });
  assert.equal(applied.status, 200, JSON.stringify(applied.body));
  const login = await post(authUrl + "accounts:signInWithPassword?key=emulator", { email: newEmail, password: "Test-only-123!", returnSecureToken: true });
  assert.equal(login.status, 200, JSON.stringify(login.body));
  const sync = await callable("syncAccountContact", {}, login.body.idToken);
  assert.equal(sync.body.result.email, newEmail);
  assert.equal(sync.body.result.emailVerified, true);
});

test("posting, editing, own-list query and favorite/unfavorite obey real rules", async () => {
  const user = await account("listing"); const other = await account("other");
  const postId = `marketplace-${Date.now()}`;
  // Seed only timestamps; all listing edits below use the user's token and rules.
  await db.doc(`posts/${postId}`).set({ ownerId: user.uid, title: "Áo kiểm thử", description: "Mô tả", type: "SALE", price: 100000,
    condition: "GOOD", status: "DRAFT", createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
  assert.equal((await patchDoc(`posts/${postId}`, { status: "ACTIVE" }, user.token)).status, 200);
  assert.equal((await patchDoc(`posts/${postId}`, { title: "Áo đã sửa", price: 120000 }, user.token)).status, 200);
  assert.equal((await patchDoc(`posts/${postId}`, { title: "Sửa trái phép" }, other.token)).status, 403);
  const own = await post(docUrl.slice(0, -1) + ":runQuery", { structuredQuery: { from: [{ collectionId: "posts" }],
    where: { fieldFilter: { field: { fieldPath: "ownerId" }, op: "EQUAL", value: { stringValue: user.uid } } }
  } }, user.token);
  assert.equal(own.status, 200, JSON.stringify(own.body));
  assert.ok(own.body.some(row => row.document?.fields.title.stringValue === "Áo đã sửa"));
  const favoritePath = `users/${other.uid}/favorites/${postId}`;
  const favorite = await fetch(docUrl + favoritePath, { method: "PATCH", headers: {
    "content-type": "application/json", authorization: `Bearer ${other.token}`
  }, body: JSON.stringify({ fields: { postId: { stringValue: postId }, createdAt: { timestampValue: new Date().toISOString() } } }) });
  assert.equal(favorite.status, 200, await favorite.text());
  const removed = await fetch(docUrl + favoritePath, { method: "DELETE", headers: { authorization: `Bearer ${other.token}` } });
  assert.equal(removed.status, 200);
});
