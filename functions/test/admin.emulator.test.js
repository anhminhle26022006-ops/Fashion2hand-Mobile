const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const { verifyFixture } = require("./verified-fixture");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
process.env.FIREBASE_STORAGE_EMULATOR_HOST = "127.0.0.1:9199";
const app = initializeApp({ projectId: "fashion2hand" }, `admin-${Date.now()}`), db = getFirestore(app), auth = getAuth(app);
after(() => deleteApp(app));
async function http(url, data, token, method = "POST") {
 const response = await fetch(url, { method, headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(data), signal: AbortSignal.timeout(60000) });
 return { status: response.status, body: await response.json() };
}
const call = (name, data, user) => http(`http://127.0.0.1:5001/fashion2hand/us-central1/${name}`, { data }, user?.token);
async function ok(name, data, user) { const result = await call(name, data, user); assert.equal(result.status, 200, JSON.stringify(result.body)); return result.body.result; }
async function denied(name, data, user) { const result = await call(name, data, user); assert.ok(result.status >= 400, JSON.stringify(result.body)); }
async function login(email) { const result = await http("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=test", { email, password: "Test-only-123!", returnSecureToken: true }); assert.equal(result.status, 200); return result.body.idToken; }
async function user(role = "USER", claim = false) {
 const email = `admin-test-${Date.now()}-${Math.random()}@example.com`;
 const record = await auth.createUser({ email, password: "Test-only-123!" });
 if (claim) await auth.setCustomUserClaims(record.uid, { admin: true });
 await db.doc(`users/${record.uid}`).set({ uid: record.uid, name: email, email, role, accountStatus: "ACTIVE", createdAt: FieldValue.serverTimestamp() });
 await db.doc(`users/${record.uid}/addresses/home`).set({ recipient: "Test", phone: "0901234567", province: "HCM", district: "Q1", ward: "Ward", street: "1 Test" });
 return verifyFixture(app, { uid: record.uid, email, token: await login(email) });
}
async function mutate(admin, action, id, value, note = "Kiểm thử quản trị", requestId = `r-${Date.now()}-${Math.random()}`) { return ok("adminMutate", { action, id, value, note, requestId }, admin); }
const docs = "http://127.0.0.1:8080/v1/projects/fashion2hand/databases/(default)/documents/";
async function sale(seller) { const ref = db.collection("posts").doc(); await ref.set({ ownerId: seller.uid, title: `Admin listing ${ref.id}`, description: "Test", type: "SALE", price: 120000, status: "ACTIVE", condition: "GOOD", createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() }); return ref.id; }
async function shipping(seller, buyer) {
 const postId = await sale(seller); const { orderId } = await ok("createPaidOrder", { postId, addressId: "home", idempotencyKey: `order-${Math.random()}` }, buyer);
 await ok("simulatePaymentResult", { orderId, status: "PAID" }, buyer);
 for (const status of ["PREPARING", "READY_TO_SHIP"]) await ok("updateOrderStatus", { orderId, status }, seller);
 await ok("createShipment", { orderId, carrier: "MOCK_CARRIER" }, seller);
 await ok("createDispute", { orderId, reason: "DAMAGED", description: "Sản phẩm bị hỏng cần kiểm tra", uploadKey: `e-${Math.random()}`, evidencePaths: [] }, buyer);
 return { orderId, postId };
}
test("Admin access requires authentication, current claim AND current active server role", async () => {
 const ordinary = await user(), roleOnly = await user("ADMIN"), claimOnly = await user("USER", true), admin = await user("ADMIN", true);
 for (const who of [null, ordinary, roleOnly, claimOnly]) {
   for (const [name, data] of [["adminSession", {}], ["adminDashboard", {}], ["adminList", { collection: "users" }], ["adminDetail", { collection: "users", id: admin.uid }], ["adminSettings", {}], ["adminMutate", { action: "userLock", id: ordinary.uid, value: { locked: true }, requestId: "denied" }]]) await denied(name, data, who);
 }
 assert.equal((await ok("adminSession", {}, admin)).uid, admin.uid);
 await auth.setCustomUserClaims(admin.uid, {}); await denied("adminDashboard", {}, admin);
 await auth.setCustomUserClaims(admin.uid, { admin: true }); await db.doc(`users/${admin.uid}`).update({ role: "USER" }); await denied("adminList", { collection: "users" }, admin);
 const escalation = await http(docs + `users/${ordinary.uid}?updateMask.fieldPaths=role`, { fields: { role: { stringValue: "ADMIN" } } }, ordinary.token, "PATCH"); assert.equal(escalation.status, 403);
});
test("Dashboard, paginated lists, detail and audit use persisted data", async () => {
 const admin = await user("ADMIN", true), seller = await user(); const ids = await Promise.all(Array.from({ length: 4 }, () => sale(seller)));
 const dashboard = await ok("adminDashboard", {}, admin); assert.ok(dashboard.users >= 2); assert.ok(dashboard.posts >= 4);
 let cursor = "", found = new Set();
 do { const result = await ok("adminList", { collection: "posts", limit: 3, cursor }, admin); assert.ok(result.records.length <= 3); for (const record of result.records) { assert.ok(!found.has(record.id)); found.add(record.id); } cursor = result.nextCursor; } while (cursor);
 for (const id of ids) assert.ok(found.has(id));
 const detail = await ok("adminDetail", { collection: "posts", id: ids[0] }, admin); assert.equal(detail.record.ownerId, seller.uid);
 await denied("adminList", { collection: "secret" }, admin); await denied("adminList", { collection: "posts", limit: 999 }, admin);
 const direct = await http(docs + "systemConfig/active", { fields: { maxPrice: { integerValue: "1" } } }, admin.token, "PATCH"); assert.equal(direct.status, 403);
});
test("Lock disables Auth and blocks old-token callable, Firestore and Storage writes; moderation cannot be undone by owner", async () => {
 const admin = await user("ADMIN", true), target = await user(); const postId = await sale(target);
 await mutate(admin, "postStatus", postId, { status: "HIDDEN" }); assert.equal((await db.doc(`posts/${postId}`).get()).get("moderationLocked"), true);
 const reactivate = await http(docs + `posts/${postId}?updateMask.fieldPaths=status`, { fields: { status: { stringValue: "ACTIVE" } } }, target.token, "PATCH"); assert.equal(reactivate.status, 403);
 await mutate(admin, "userLock", target.uid, { locked: true }); assert.equal((await auth.getUser(target.uid)).disabled, true);
 await denied("getTransactionCapabilities", {}, target);
 const edit = await http(docs + `users/${target.uid}?updateMask.fieldPaths=name`, { fields: { name: { stringValue: "Bypass" } } }, target.token, "PATCH"); assert.equal(edit.status, 403);
 const image = await fetch(`http://127.0.0.1:9199/v0/b/fashion2hand.firebasestorage.app/o?uploadType=media&name=users/${target.uid}/avatar/locked.jpg`, { method: "POST", headers: { authorization: `Bearer ${target.token}`, "content-type": "image/jpeg" }, body: Buffer.from([255,216,255,217]) }); assert.equal(image.status, 403);
 const unlockKey = `unlock-${Math.random()}`;
 await mutate(admin, "userLock", target.uid, { locked: false }, "Mở khóa kiểm thử", unlockKey); assert.equal((await auth.getUser(target.uid)).disabled, false); target.token = await login(target.email); await ok("getTransactionCapabilities", {}, target);
 await mutate(admin, "userLock", target.uid, { locked: true });
 await mutate(admin, "userLock", target.uid, { locked: false }, "Mở khóa kiểm thử", unlockKey);
 assert.equal((await auth.getUser(target.uid)).disabled, true, "Retrying an old unlock must preserve a newer lock");
 assert.equal((await db.doc(`users/${target.uid}`).get()).get("accountStatus"), "LOCKED");
 await mutate(admin, "userLock", target.uid, { locked: false });
 await denied("adminMutate", { action: "userLock", id: admin.uid, value: { locked: true }, requestId: "self-lock" }, admin);
});
test("Settings validate, persist, reject stale saves and deduplicate identical retries", async () => {
 const admin = await user("ADMIN", true), config = await ok("adminSettings", {}, admin), requestId = `settings-${Math.random()}`;
 const value = { ...config, maxPrice: 3000000, shippingFee: 15000 };
 const first = await mutate(admin, "settings", "active", value, "Cập nhật cấu hình", requestId); const again = await mutate(admin, "settings", "active", value, "Cập nhật cấu hình", requestId); assert.equal(first.auditId, again.auditId);
 assert.equal((await ok("adminSettings", {}, admin)).maxPrice, 3000000);
 await denied("adminMutate", { action: "settings", id: "active", value, requestId: "stale" }, admin);
 await denied("adminMutate", { action: "settings", id: "active", value: { ...value, revision: value.revision + 1, reportThreshold: 0 }, requestId: "invalid" }, admin);
 const audit = await db.doc(`adminAudit/${admin.uid}_${requestId}`).get(); assert.equal(audit.get("action"), "settings"); assert.equal(audit.get("actorId"), admin.uid);
});
test("Reports, warnings and verification decisions persist and cannot be overwritten", async () => {
 const admin = await user("ADMIN", true), target = await user(); const report = db.collection("reports").doc(), verification = db.collection("verificationRequests").doc();
 await report.set({ reporterId: target.uid, targetType: "USER", targetId: target.uid, reason: "Spam", description: "Test", status: "PENDING" });
 await verification.set({ userId: target.uid, status: "PENDING", requestType: "IDENTITY", evidencePaths: [] });
 await mutate(admin, "report", report.id, { status: "RESOLVED" }); assert.equal((await report.get()).get("status"), "RESOLVED");
 await mutate(admin, "verification", verification.id, { approve: true }); assert.equal((await db.doc(`users/${target.uid}`).get()).get("verificationStatus"), "VERIFIED");
 await denied("adminMutate", { action: "verification", id: verification.id, value: { approve: false }, note: "Thử thay đổi", requestId: "second-decision" }, admin);
 await mutate(admin, "userWarning", target.uid, {}, "Cảnh báo nội dung kiểm thử"); assert.equal((await db.collection(`users/${target.uid}/notifications`).where("type", "==", "ADMIN_WARNING").get()).size, 1);
});
test("Dispute decisions atomically refund or release once, WAITING_EVIDENCE blocks buyer payout, and evidence stays private", async () => {
 const admin = await user("ADMIN", true), seller = await user(), buyer = await user(), outsider = await user();
 for (const resolution of ["REFUND", "RELEASE"]) {
   const { orderId } = await shipping(seller, buyer);
   await mutate(admin, "disputeEvidence", orderId, {}, "Bổ sung ảnh hàng bị hỏng");
   await ok("simulateShipmentUpdate", { orderId, status: "DELIVERED", description: "Đã giao kiểm thử" }, seller);
   await denied("updateOrderStatus", { orderId, status: "COMPLETED" }, buyer);
   const key = `resolution-${Math.random()}`; await mutate(admin, "disputeResolve", orderId, { resolution }, "Đã xem xét bằng chứng", key); await mutate(admin, "disputeResolve", orderId, { resolution }, "Đã xem xét bằng chứng", key);
   const order = await db.doc(`orders/${orderId}`).get(); assert.equal(order.get("orderStatus"), resolution === "REFUND" ? "REFUNDED" : "COMPLETED"); assert.equal(order.get("disputeStatus"), "RESOLVED");
   assert.equal((await db.collection("moneyTransactions").where("orderId", "==", orderId).get()).size, 2);
   const detail = await ok("adminDetail", { collection: "orders", id: orderId }, admin); assert.ok(detail.timeline.length >= 5); assert.ok(detail.shipment.events.length >= 2); assert.equal(detail.money.length, 2);
   await denied("adminMutate", { action: "disputeResolve", id: orderId, value: { resolution: "REFUND" }, note: "Không được giải quyết lại", requestId: "repeat-decision" }, admin);
 }
 const ref = db.collection("disputes").doc(); const path = `disputeEvidence/${ref.id}/${buyer.uid}/test/0.jpg`;
 await getStorage(app).bucket("fashion2hand.firebasestorage.app").file(path).save(Buffer.from([255,216,255,217]), { metadata: { contentType: "image/jpeg" } });
 await ref.set({ buyerId: buyer.uid, sellerId: seller.uid, evidencePaths: [path], evidenceBucket: "fashion2hand.firebasestorage.app" });
 assert.ok((await ok("adminEvidence", { collection: "disputes", id: ref.id, path }, admin)).base64);
 await denied("adminEvidence", { collection: "disputes", id: ref.id, path }, outsider); await denied("adminEvidence", { collection: "disputes", id: ref.id, path: "other/file.jpg" }, admin);
});
