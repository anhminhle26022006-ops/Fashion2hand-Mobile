const { test, after } = require("node:test");
const assert = require("node:assert/strict");
// Invoke the real callable handlers against local Auth/Firestore, without SMS.
process.env.GCLOUD_PROJECT = "fashion2hand";
process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: "fashion2hand", storageBucket: "fashion2hand.firebasestorage.app" });
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
process.env.FUNCTIONS_EMULATOR = "true";
process.env.ENABLE_SIMULATED_PAYMENTS = "true";
process.env.ENABLE_SIMULATED_SHIPPING = "true";
const handlers = require("../index");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { deleteApp, getApp } = require("firebase-admin/app");
const auth = getAuth(), db = getFirestore();
const created = [];
after(async () => {
  await Promise.all(created.map(uid => auth.deleteUser(uid)));
  await deleteApp(getApp());
});
async function account(label, emailVerified) {
  const user = await auth.createUser({ email: `${label}-${Date.now()}-${Math.random().toString(16).slice(2)}@example.test`,
    password: "Test-only-123!", emailVerified });
  created.push(user.uid);
  await db.doc(`users/${user.uid}`).set({ uid: user.uid, name: label, role: "USER", accountStatus: "ACTIVE",
    emailVerified: true, phoneVerified: true }); // Forged profile flags must never grant access.
  await db.doc(`users/${user.uid}/addresses/home`).set({ recipient: label, phone: "0901234567",
    province: "Thành phố Hà Nội", district: "", ward: "Phường Ba Đình", provinceCode: "1", wardCode: "4", addressVersion: "VN_2_LEVEL_2025", street: "1 Đường kiểm thử" });
  return user;
}
function request(user, data = {}) {
  return { auth: { uid: user.uid, token: { email_verified: true, phone_number: "+10000000000" } }, data };
}
async function listing(seller, type = "SALE") {
  const post = db.collection("posts").doc();
  await post.set({ ownerId: seller.uid, title: "Email-only policy test", description: "Kiểm thử không SMS",
    type, price: type === "SALE" ? 120000 : 0, condition: "GOOD", status: "ACTIVE",
    createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
  return post.id;
}

test("unverified email is rejected by paid, cart and free handlers despite forged claims/profile flags", async () => {
  const user = await account("unverified-email", false);
  for (const handler of [handlers.createPaidOrder, handlers.createCartOrders, handlers.createFreeRequest])
    await assert.rejects(handler.run(request(user)), error => error.code === "failed-precondition" && /email/.test(error.message));
});

test("verified email without any linked phone can buy, request free goods and check out a cart", async () => {
  const seller = await account("email-only-seller", true), buyer = await account("email-only-buyer", true);
  assert.equal((await auth.getUser(buyer.uid)).phoneNumber, undefined);
  const sale = await listing(seller), free = await listing(seller, "FREE"), cart = await listing(seller);
  const paid = await handlers.createPaidOrder.run(request(buyer, { postId: sale, addressId: "home", idempotencyKey: "email-only-paid" }));
  assert.ok(paid.orderId);
  const received = await handlers.createFreeRequest.run(request(buyer, { postId: free, addressId: "home", note: "Xin nhận" }));
  assert.ok(received.requestId);
  await db.doc(`users/${buyer.uid}/cartItems/${cart}`).set({ postId: cart });
  const quote = await handlers.getCartCheckoutQuote.run(request(buyer, { postIds: [cart] }));
  const checkedOut = await handlers.createCartOrders.run(request(buyer, { postIds: [cart], addressId: "home",
    idempotencyKey: "email-only-cart", expectedTotal: quote.total }));
  assert.equal(checkedOut.orderIds.length, 1);
  assert.equal((await auth.getUser(buyer.uid)).phoneNumber, undefined);
});

test("disabled accounts remain rejected even with verified email", async () => {
  const user = await account("disabled-email-only", true);
  await auth.updateUser(user.uid, { disabled: true });
  for (const handler of [handlers.createPaidOrder, handlers.createCartOrders, handlers.createFreeRequest])
    await assert.rejects(handler.run(request(user)), error => error.code === "failed-precondition");
});

test("active Admin with server claim and ADMIN profile is exempt from email and phone verification", async () => {
  const admin = await account("unverified-active-admin", false), seller = await account("admin-test-seller", true);
  await auth.setCustomUserClaims(admin.uid, { admin: true });
  await db.doc(`users/${admin.uid}`).update({ role: "ADMIN" });
  assert.equal((await auth.getUser(admin.uid)).emailVerified, false);
  assert.equal((await auth.getUser(admin.uid)).phoneNumber, undefined);
  assert.equal((await handlers.adminSession.run(request(admin))).uid, admin.uid);
  const sale = await listing(seller), free = await listing(seller, "FREE"), cart = await listing(seller);
  assert.ok((await handlers.createPaidOrder.run(request(admin, {
    postId: sale, addressId: "home", idempotencyKey: "admin-exempt-paid" }))).orderId);
  assert.ok((await handlers.createFreeRequest.run(request(admin, { postId: free, addressId: "home" }))).requestId);
  await db.doc(`users/${admin.uid}/cartItems/${cart}`).set({ postId: cart });
  const quote = await handlers.getCartCheckoutQuote.run(request(admin, { postIds: [cart] }));
  assert.equal((await handlers.createCartOrders.run(request(admin, { postIds: [cart], addressId: "home",
    idempotencyKey: "admin-exempt-cart", expectedTotal: quote.total }))).orderIds.length, 1);
  await auth.updateUser(admin.uid, { disabled: true });
  await assert.rejects(handlers.createPaidOrder.run(request(admin)), error => error.code === "failed-precondition");
});

test("profile role alone or custom claim alone cannot obtain the Admin verification exemption", async () => {
  const profileOnly = await account("profile-only-admin", false), claimOnly = await account("claim-only-admin", false);
  await db.doc(`users/${profileOnly.uid}`).update({ role: "ADMIN" });
  await auth.setCustomUserClaims(claimOnly.uid, { admin: true });
  for (const user of [profileOnly, claimOnly]) {
    await assert.rejects(handlers.adminSession.run(request(user)), error => error.code === "permission-denied");
    for (const handler of [handlers.createPaidOrder, handlers.createCartOrders, handlers.createFreeRequest])
      await assert.rejects(handler.run(request(user)), error => error.code === "failed-precondition");
  }
});
