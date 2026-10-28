const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const app = initializeApp({ projectId: "fashion2hand" }, `moderation-${Date.now()}`), db = getFirestore(app), auth = getAuth(app);
const configRef = db.doc("systemConfig/active");
let savedConfig = null;
after(async () => { if (savedConfig) await configRef.set(savedConfig); else await configRef.delete(); await deleteApp(app); });

async function http(url, data, token, method = "POST") {
  const response = await fetch(url, { method, headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(data), signal: AbortSignal.timeout(60000) });
  return { status: response.status, body: await response.json() };
}
const call = (name, data, user) => http(`http://127.0.0.1:5001/fashion2hand/us-central1/${name}`, { data }, user?.token);
async function ok(name, data, user) { const result = await call(name, data, user); assert.equal(result.status, 200, JSON.stringify(result.body)); return result.body.result; }
async function denied(name, data, user) { const result = await call(name, data, user); assert.ok(result.status >= 400, JSON.stringify(result.body)); }
async function login(email) { const result = await http("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=test", { email, password: "Test-only-123!", returnSecureToken: true }); assert.equal(result.status, 200); return result.body.idToken; }
async function user(extra = {}) {
  const email = `moderation-${Date.now()}-${Math.random()}@example.com`;
  const record = await auth.createUser({ email, password: "Test-only-123!", emailVerified: true });
  await db.doc(`users/${record.uid}`).set({ uid: record.uid, name: email, email, role: "USER", accountStatus: "ACTIVE", createdAt: FieldValue.serverTimestamp(), ...extra });
  return { uid: record.uid, email, token: await login(email) };
}
async function post(owner, extra = {}) {
  const ref = db.collection("posts").doc();
  await ref.set({ ownerId: owner.uid, title: `Moderation ${ref.id}`, description: "Test", type: "SALE", price: 100000, status: "ACTIVE", condition: "GOOD", createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(), ...extra });
  return ref.id;
}
async function policy(values) {
  if (savedConfig === null) { const doc = await configRef.get(); savedConfig = doc.exists ? doc.data() : false; }
  await configRef.set(values, { merge: true });
}
const report = (targetType, targetId, reason = "Hàng giả / hàng nhái") => ({ targetType, targetId, reason, description: "Kiểm thử báo cáo" });
const docs = "http://127.0.0.1:8080/v1/projects/fashion2hand/databases/(default)/documents/";

test("submitReport validates the reporter, the target and duplicates", async () => {
  const owner = await user(), reporter = await user(); const postId = await post(owner);
  await denied("submitReport", report("POST", postId), null);
  const unverified = await user(); await auth.updateUser(unverified.uid, { emailVerified: false });
  await denied("submitReport", report("POST", postId), unverified);
  await denied("submitReport", report("ORDER", postId), reporter);
  await denied("submitReport", report("POST", "missing-post"), reporter);
  await denied("submitReport", report("POST", postId), owner);
  await denied("submitReport", report("USER", reporter.uid), reporter);
  const first = await ok("submitReport", report("POST", postId), reporter);
  assert.equal(first.status, "PENDING");
  const stored = await db.doc(`reports/${first.id}`).get();
  assert.equal(stored.get("reporterId"), reporter.uid); assert.equal(stored.get("targetId"), postId);
  await denied("submitReport", report("POST", postId), reporter);
  assert.equal((await ok("submitReport", report("USER", owner.uid, "Spam / quảng cáo"), reporter)).status, "PENDING");
});

test("Post is auto-hidden only when the policy is on and distinct reporters reach the threshold", async () => {
  const owner = await user(), a = await user(), b = await user(), c = await user();
  await policy({ autoHideReported: false, reportThreshold: 2 });
  const off = await post(owner);
  for (const who of [a, b]) await ok("submitReport", report("POST", off), who);
  assert.equal((await db.doc(`posts/${off}`).get()).get("status"), "ACTIVE");

  await policy({ autoHideReported: true, reportThreshold: 2 });
  const on = await post(owner);
  assert.equal((await ok("submitReport", report("POST", on), a)).autoHidden, false);
  assert.equal((await db.doc(`posts/${on}`).get()).get("status"), "ACTIVE");
  assert.equal((await ok("submitReport", report("POST", on), b)).autoHidden, true);
  const hidden = await db.doc(`posts/${on}`).get();
  assert.equal(hidden.get("status"), "HIDDEN"); assert.equal(hidden.get("moderationLocked"), true); assert.equal(hidden.get("moderatedBy"), "SYSTEM");
  const audit = await db.collection("adminAudit").where("targetId", "==", on).get();
  assert.equal(audit.docs.filter(doc => doc.get("action") === "autoHidePost").length, 1);
  const notes = await db.collection(`users/${owner.uid}/notifications`).where("type", "==", "POST_AUTO_HIDDEN").get();
  assert.equal(notes.size, 1);

  const unhide = await http(docs + `posts/${on}?updateMask.fieldPaths=status`, { fields: { status: { stringValue: "ACTIVE" } } }, owner.token, "PATCH");
  assert.equal(unhide.status, 403);

  const reserved = await post(owner, { reservedForOrderId: "order-in-progress" });
  for (const who of [a, c]) await ok("submitReport", report("POST", reserved), who);
  assert.equal((await db.doc(`posts/${reserved}`).get()).get("status"), "ACTIVE");
});

test("Clients cannot write reports directly; they must use submitReport", async () => {
  const reporter = await user(), owner = await user(); const postId = await post(owner);
  const direct = await http(docs + "reports", { fields: { reporterId: { stringValue: reporter.uid }, targetType: { stringValue: "POST" },
    targetId: { stringValue: postId }, reason: { stringValue: "Spam" }, status: { stringValue: "RESOLVED" } } }, reporter.token);
  assert.equal(direct.status, 403);
});

test("Rules enforce maxPrice for client-created posts and edits", async () => {
  await policy({ maxPrice: 500000 });
  const seller = await user();
  const fields = (type, price) => ({ fields: { ownerId: { stringValue: seller.uid }, title: { stringValue: "Áo kiểm thử" }, description: { stringValue: "Test" },
    type: { stringValue: type }, price: { integerValue: String(price) }, condition: { stringValue: "GOOD" }, status: { stringValue: "ACTIVE" },
    createdAt: { timestampValue: new Date().toISOString() }, updatedAt: { timestampValue: new Date().toISOString() } } });
  const id = () => `rules-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  assert.equal((await http(docs + `posts?documentId=${id()}`, fields("SALE", 600000), seller.token)).status, 403);
  const okId = id();
  assert.equal((await http(docs + `posts?documentId=${okId}`, fields("SALE", 500000), seller.token)).status, 200);
  const raise = await http(docs + `posts/${okId}?updateMask.fieldPaths=price`, { fields: { price: { integerValue: "900000" } } }, seller.token, "PATCH");
  assert.equal(raise.status, 403);
  await policy({ maxPrice: 2000000 });
  assert.equal((await http(docs + `posts/${okId}?updateMask.fieldPaths=price`, { fields: { price: { integerValue: "900000" } } }, seller.token, "PATCH")).status, 200);
});
