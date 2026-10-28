const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
process.env.FIREBASE_STORAGE_EMULATOR_HOST = "127.0.0.1:9199";
const app = initializeApp({ projectId: "fashion2hand" }, `account-${Date.now()}`), db = getFirestore(app), auth = getAuth(app);
after(() => deleteApp(app));
async function http(url, data, token, method = "POST") {
  const response = await fetch(url, { method, headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(data), signal: AbortSignal.timeout(120000) });
  return { status: response.status, body: await response.json() };
}
const call = (name, data, who) => http(`http://127.0.0.1:5001/fashion2hand/us-central1/${name}`, { data }, who?.token);
async function ok(name, data, who) { const result = await call(name, data, who); assert.equal(result.status, 200, JSON.stringify(result.body)); return result.body.result; }
async function denied(name, data, who) { const result = await call(name, data, who); assert.ok(result.status >= 400, `${name}: ${JSON.stringify(result.body)}`); }
async function user(admin = false) {
  const email = `account-qa-${Date.now()}-${Math.random()}@example.com`, record = await auth.createUser({ email, password: "Test-only-123!" });
  if (admin) await auth.setCustomUserClaims(record.uid, { admin: true });
  await db.doc(`users/${record.uid}`).set({ uid: record.uid, name: "Tài khoản kiểm thử", email, role: admin ? "ADMIN" : "USER", accountStatus: "ACTIVE", createdAt: FieldValue.serverTimestamp() });
  const login = await http("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=emulator", { email, password: "Test-only-123!", returnSecureToken: true });
  assert.equal(login.status, 200); return { uid: record.uid, token: login.body.idToken };
}
const address = { recipient: "Người nhận kiểm thử", phone: "0901234567", province: "TP HCM", district: "Quận 1", ward: "Phường 1", street: "1 Đường kiểm thử", isDefault: true, label: "HOME" };

test("Chat context returns only public peer data and actual buying/selling roles to participants", async () => {
  const seller = await user(), buyer = await user(), outsider = await user();
  await db.doc(`users/${seller.uid}`).update({ name: "Người bán thật", phone: "0901234567", address: "Địa chỉ riêng tư" });
  const post = db.collection("posts").doc(); await post.set({ ownerId: seller.uid, title: "Món kiểm thử" });
  const chat = db.collection("chats").doc(); await chat.set({ participantIds: [seller.uid, buyer.uid], postId: post.id });
  const buying = await ok("getChatContext", { chatId: chat.id }, buyer); assert.equal(buying.role, "buy"); assert.equal(buying.name, "Người bán thật"); assert.equal(buying.phone, undefined); assert.equal(buying.address, undefined);
  assert.equal((await ok("getChatContext", { chatId: chat.id }, seller)).role, "sell");
  await denied("getChatContext", { chatId: chat.id }, outsider); await denied("getChatContext", { chatId: chat.id }, null);
});

test("Saved address book and checkout share backend documents, enforce owner and one default", async () => {
  const owner = await user(), other = await user();
  await denied("saveAddress", { id: "home", address }, null);
  await denied("saveAddress", { id: "home", address: { ...address, phone: "bad" } }, owner);
  await ok("saveAddress", { id: "home", address }, owner);
  await ok("saveAddress", { id: "work", address: { ...address, label: "WORK" } }, owner);
  await ok("saveAddress", { id: "work", address: { ...address, label: "WORK" } }, owner);
  let docs = await db.collection(`users/${owner.uid}/addresses`).get(); assert.equal(docs.size, 2);
  assert.equal(docs.docs.filter(doc => doc.get("isDefault")).length, 1); assert.equal((await db.doc(`users/${owner.uid}/addresses/home`).get()).get("isDefault"), false);
  await ok("deleteAddress", { id: "work", uid: owner.uid }, other); assert.equal((await db.doc(`users/${owner.uid}/addresses/work`).get()).exists, true);
  await ok("deleteAddress", { id: "work" }, owner); assert.equal((await db.doc(`users/${owner.uid}/addresses/home`).get()).get("isDefault"), true);
  await db.doc(`users/${owner.uid}`).update({ accountStatus: "LOCKED" }); await denied("saveAddress", { id: "new", address }, owner);
});

test("Payout details persist privately and never claim bank verification", async () => {
  const owner = await user(), other = await user();
  const value = { bank: "MB Bank", holder: "CHU TAI KHOAN", accountNumber: "0123456789", consent: true };
  await denied("savePayoutAccount", { ...value, consent: false }, owner);
  await ok("savePayoutAccount", value, owner);
  const saved = (await ok("getAccountData", {}, owner)).payout; assert.equal(saved.accountNumber, value.accountNumber); assert.equal(saved.verified, false);
  assert.equal((await ok("getAccountData", { uid: owner.uid }, other)).payout, null);
  const direct = await fetch(`http://127.0.0.1:8080/v1/projects/fashion2hand/databases/(default)/documents/privateAccounts/${owner.uid}`, { headers: { authorization: `Bearer ${other.token}` } }); assert.equal(direct.status, 403);
});

test("Identity submission stores private evidence, survives retries and reaches Admin review", async () => {
  const owner = await user(), other = await user(), admin = await user(true);
  const bytes = Buffer.alloc(128); bytes.set([0xff, 0xd8, 0xff]); bytes.set([0xff, 0xd9], 126);
  const image = bytes.toString("base64");
  const request = { requestId: "identity", legalName: "Người kiểm thử", birthDate: "01/01/2000", documentType: "CCCD gắn chip", identityNumber: "012345678901", accurate: true, consent: true, photos: { front: image, back: image, selfie: image } };
  await denied("submitVerification", { ...request, birthDate: "31/02/2000" }, owner);
  await denied("submitVerification", { ...request, photos: { front: image } }, owner);
  const first = await ok("submitVerification", request, owner), again = await ok("submitVerification", request, owner); assert.equal(first.id, again.id);
  const stored = await db.doc(`verificationRequests/${first.id}`).get(); assert.equal(stored.get("status"), "PENDING"); assert.equal(stored.get("evidencePaths").length, 3);
  const file = getStorage(app).bucket(stored.get("evidenceBucket")).file(stored.get("evidencePaths")[0]); assert.equal((await file.getMetadata())[0].contentType, "image/jpeg");
  const own = await ok("getAccountData", {}, owner); assert.equal(own.verification.id, first.id); assert.equal(own.verification.identityNumber, undefined); assert.equal(own.verification.evidencePaths, undefined);
  assert.equal((await ok("getAccountData", { uid: owner.uid }, other)).verification, null);
  await denied("submitVerification", { ...request, requestId: "duplicate" }, owner);
  await ok("adminMutate", { action: "verification", id: first.id, requestId: "reject-identity", value: { approve: false }, note: "Ảnh cần rõ hơn" }, admin);
  assert.equal((await ok("getAccountData", {}, owner)).verification.status, "REJECTED");
  const second = await ok("submitVerification", { ...request, requestId: "resubmit" }, owner);
  await ok("adminMutate", { action: "verification", id: second.id, requestId: "approve-identity", value: { approve: true }, note: "Đã đối chiếu hồ sơ kiểm thử" }, admin);
  assert.equal((await ok("getAccountData", {}, owner)).verificationStatus, "VERIFIED");
  const events = await db.collection(`users/${owner.uid}/notifications`).where("type", "==", "VERIFICATION_UPDATED").get(); assert.equal(events.size, 2);
  await denied("submitVerification", { ...request, requestId: "after-approved" }, owner);
  const imageResponse = await fetch(`http://127.0.0.1:9199/v0/b/${stored.get("evidenceBucket")}/o/${encodeURIComponent(stored.get("evidencePaths")[0])}?alt=media`, { headers: { authorization: `Bearer ${other.token}` } }); assert.equal(imageResponse.status, 403);
});

test("Verification rules deny direct access and approval rejects forged, stale or incomplete evidence", async () => {
  const owner = await user(), other = await user(), admin = await user(true);
  const docs = "http://127.0.0.1:8080/v1/projects/fashion2hand/databases/(default)/documents/verificationRequests";
  const direct = { fields: { userId: { stringValue: owner.uid }, status: { stringValue: "PENDING" } } };
  // No client may create or query private identity submissions, including an Admin client.
  for (const actor of [null, owner, other, admin]) {
    const headers = actor ? { authorization: `Bearer ${actor.token}` } : {};
    assert.equal((await fetch(docs, { headers })).status, 403);
    assert.equal((await http(`${docs}/${owner.uid}_forged`, direct, actor?.token, "PATCH")).status, 403);
  }
  const forged = db.doc(`verificationRequests/${owner.uid}_forged`);
  await forged.set({ userId: owner.uid, status: "PENDING", evidencePaths: [] });
  await db.doc(`users/${owner.uid}`).update({ verificationRequestId: forged.id, verificationStatus: "PENDING" });
  await denied("adminMutate", { action: "verification", id: forged.id, requestId: "empty-evidence", value: { approve: true } }, admin);
  assert.equal((await db.doc(`users/${owner.uid}`).get()).get("verificationStatus"), "PENDING");
  for (const actor of [owner, other, admin]) {
    assert.equal((await fetch(`${docs}/${forged.id}`, { headers: { authorization: `Bearer ${actor.token}` } })).status, 403);
    assert.equal((await http(`${docs}/${forged.id}`, { fields: { status: { stringValue: "APPROVED" } } }, actor.token, "PATCH")).status, 403);
    assert.equal((await fetch(`${docs}/${forged.id}`, { method: "DELETE", headers: { authorization: `Bearer ${actor.token}` } })).status, 403);
  }
  await db.doc(`users/${owner.uid}`).update({ verificationStatus: "UNVERIFIED" });
  const bytes = Buffer.alloc(128); bytes.set([0xff, 0xd8, 0xff]); bytes.set([0xff, 0xd9], 126);
  const image = bytes.toString("base64");
  const submitted = await ok("submitVerification", { requestId: "evidence-check", legalName: "Evidence fixture", birthDate: "01/01/2000", documentType: "CCCD gắn chip", identityNumber: "012345678901", accurate: true, consent: true, photos: { front: image, back: image, selfie: image } }, owner);
  const ref = db.doc(`verificationRequests/${submitted.id}`), original = (await ref.get()).data();
  let attempt = 0;
  const rejectApproval = async () => {
    await denied("adminMutate", { action: "verification", id: ref.id, requestId: `bad-evidence-${attempt++}`, value: { approve: true } }, admin);
    assert.equal((await ref.get()).get("status"), "PENDING");
    assert.equal((await db.doc(`users/${owner.uid}`).get()).get("verificationStatus"), "PENDING");
  };
  await db.doc(`users/${owner.uid}`).update({ verificationRequestId: forged.id }); await rejectApproval();
  await db.doc(`users/${owner.uid}`).update({ verificationRequestId: ref.id });
  for (const evidencePaths of [[], [original.evidencePaths[0], original.evidencePaths[0], original.evidencePaths[2]], original.evidencePaths.map(p => p.replace(owner.uid, other.uid))]) {
    await ref.update({ evidencePaths }); await rejectApproval();
  }
  await ref.set(original);
  const file = getStorage(app).bucket(original.evidenceBucket).file(original.evidencePaths[0]);
  await file.setMetadata({ contentType: "application/octet-stream" }); await rejectApproval();
  await file.setMetadata({ contentType: "image/jpeg" });
  await file.delete(); await rejectApproval();
  await file.save(bytes, { resumable: false, metadata: { contentType: "image/jpeg" } });
  await ok("adminMutate", { action: "verification", id: ref.id, requestId: "complete-evidence", value: { approve: true } }, admin);
  assert.equal((await db.doc(`users/${owner.uid}`).get()).get("verificationStatus"), "VERIFIED");
});

test("Support tickets are idempotent, visible to Admin and return actual resolutions to their owner", async () => {
  const owner = await user(), admin = await user(true), other = await user();
  const request = { requestId: "support", title: "Cần hỗ trợ kiểm thử", description: "Nội dung yêu cầu hỗ trợ kiểm thử từ emulator." };
  const first = await ok("submitSupportTicket", request, owner); assert.equal((await ok("submitSupportTicket", request, owner)).id, first.id);
  await denied("submitSupportTicket", { ...request, description: "Changed content with a reused key" }, owner);
  assert.equal((await ok("getAccountData", {}, owner)).tickets.length, 1); assert.equal((await ok("getAccountData", {}, other)).tickets.length, 0);
  await ok("adminMutate", { action: "report", id: first.id, requestId: "support-resolve", value: { status: "RESOLVED" }, note: "Đã kiểm tra yêu cầu hỗ trợ" }, admin);
  const ticket = (await ok("getAccountData", {}, owner)).tickets[0]; assert.equal(ticket.status, "RESOLVED"); assert.equal(ticket.resolutionNote, "Đã kiểm tra yêu cầu hỗ trợ");
  assert.equal((await db.collection(`users/${owner.uid}/notifications`).where("type", "==", "SUPPORT_UPDATED").get()).size, 1);
});
