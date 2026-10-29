const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const { verifyFixture } = require("./verified-fixture");
const { getAuth } = require("firebase-admin/auth");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const app = initializeApp({ projectId: "fashion2hand" }, `transactions-${Date.now()}`);
const db = getFirestore(app);
const docs = "http://127.0.0.1:8080/v1/projects/fashion2hand/databases/(default)/documents/";
after(() => deleteApp(app));
async function post(url, data, token) {
  const response = await fetch(url, { method: "POST", headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(data), signal: AbortSignal.timeout(90000) });
  return { status: response.status, body: await response.json() };
}
async function call(name, data, user) {
  return post(`http://127.0.0.1:5001/fashion2hand/us-central1/${name}`, { data }, user?.token);
}
async function ok(name, data, user) {
  const response = await call(name, data, user); assert.equal(response.status, 200, `${name}: ${JSON.stringify(response.body)}`); return response.body.result;
}
async function denied(name, data, user) { const result = await call(name, data, user); assert.notEqual(result.status, 200, `${name} should reject`); return result.body; }
async function account(label, verified = true) {
  const result = await post("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator", {
    email: `${label}-${Date.now()}-${Math.random().toString(16).slice(2)}@example.com`, password: "Test-only-123!", returnSecureToken: true
  });
  assert.equal(result.status, 200, JSON.stringify(result.body));
  const user = { uid: result.body.localId, token: result.body.idToken };
  await db.doc(`users/${user.uid}`).set({ uid: user.uid, name: label, email: result.body.email, role: "USER", accountStatus: "ACTIVE" });
  await db.doc(`users/${user.uid}/addresses/home`).set({ recipient: label, phone: "0901234567", province: "TP.HCM", district: "Quận 1", ward: "Bến Nghé", street: "1 Đường kiểm thử" });
  return verified ? verifyFixture(app, user) : user;
}
test("buy and free requests require verified Auth email; phone is optional and profile flags cannot bypass email", async () => {
  const seller = await account("verified-seller"), buyer = await account("unverified-buyer", false);
  const saleId = await listing(seller), freeId = await listing(seller, "FREE");
  await db.doc(`users/${buyer.uid}`).update({ emailVerified: true, phoneVerified: true, verificationStatus: "VERIFIED" });
  for (const state of [
    { emailVerified: false, phoneNumber: null },
    { emailVerified: false, phoneNumber: "+15550001234" }
  ]) {
    await getAuth(app).updateUser(buyer.uid, state);
    for (const [name, data] of [
      ["createPaidOrder", { postId: saleId, addressId: "home", idempotencyKey: "verification-denied" }],
      ["createFreeRequest", { postId: freeId, addressId: "home", message: "Xin nhận món đồ kiểm thử" }]
    ]) {
      const result = await call(name, data, buyer);
      assert.equal(result.body.error?.status, "FAILED_PRECONDITION", JSON.stringify(result.body));
    }
  }
  assert.equal((await db.collection("orders").where("buyerId", "==", buyer.uid).get()).size, 0);
  assert.equal((await db.doc(`posts/${saleId}`).get()).get("status"), "ACTIVE");
  await verifyFixture(app, buyer, { phone: false });
  await getAuth(app).updateUser(buyer.uid, { phoneNumber: null });
  await ok("createPaidOrder", { postId: saleId, addressId: "home", idempotencyKey: "verification-allowed" }, buyer);
  await ok("createFreeRequest", { postId: freeId, addressId: "home", message: "Xin nhận món đồ kiểm thử" }, buyer);
});
async function listing(seller, type = "SALE") {
  const ref = db.collection("posts").doc(); await ref.set({ ownerId: seller.uid, title: "Món đồ giao dịch " + ref.id, description: "Kiểm thử", type,
    price: type === "SALE" ? 120000 : 0, condition: "GOOD", status: "ACTIVE", createdAt: Timestamp.now(), updatedAt: Timestamp.now() }); return ref.id;
}
async function order(seller, buyer) {
  const postId = await listing(seller); const key = `key-${Date.now()}-${Math.random()}`;
  const result = await ok("createPaidOrder", { postId, addressId: "home", idempotencyKey: key }, buyer); return { postId, orderId: result.orderId };
}
async function paid(seller, buyer) {
  const result = await order(seller, buyer); await ok("simulatePaymentResult", { orderId: result.orderId, status: "PAID" }, buyer); return result;
}
async function shipping(seller, buyer) {
  const result = await paid(seller, buyer);
  for (const status of ["PREPARING", "READY_TO_SHIP"]) await ok("updateOrderStatus", { orderId: result.orderId, status }, seller);
  await ok("createShipment", { orderId: result.orderId, carrier: "MOCK_CARRIER" }, seller); return result;
}
async function read(path, user) {
  return fetch(docs + path, { headers: { authorization: `Bearer ${user.token}` }, signal: AbortSignal.timeout(30000) });
}
const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aF9sAAAAASUVORK5CYII=", "base64");
async function upload(path, user, contentType = "image/png") {
  return fetch("http://127.0.0.1:9199/v0/b/fashion2hand.firebasestorage.app/o?uploadType=media&name=" + encodeURIComponent(path), {
    method: "POST", headers: { authorization: `Firebase ${user.token}`, "content-type": contentType }, body: png, signal: AbortSignal.timeout(30000)
  });
}

test("checkout is one item, server-priced, address-bound and idempotent", async () => {
  const seller = await account("seller"); const buyer = await account("buyer"); const postId = await listing(seller);
  const capabilities = await ok("getTransactionCapabilities", {}, buyer);
  assert.equal(capabilities.simulatedPayments, true); assert.equal(capabilities.simulatedShipping, true); assert.equal(capabilities.checkoutItemsPerOrder, 20);
  const quote = await ok("getCheckoutQuote", { postId }, buyer); assert.equal(quote.price, 120000); assert.equal(quote.total, 120000 + quote.shippingFee);
  await denied("createPaidOrder", { postId, idempotencyKey: "missing-address" }, buyer);
  await denied("createPaidOrder", { postId, addressId: "home", idempotencyKey: "bad-price", expectedPrice: 1 }, buyer);
  await denied("getCheckoutQuote", { postId }, seller);
  const payload = { postId, addressId: "home", idempotencyKey: `concurrent-${postId}`, expectedPrice: quote.price, expectedShippingFee: quote.shippingFee, price: 1 };
  const [first, retry] = await Promise.all([ok("createPaidOrder", payload, buyer), ok("createPaidOrder", payload, buyer)]);
  assert.equal(first.orderId, retry.orderId);
  const record = await db.doc(`orders/${first.orderId}`).get(); assert.equal(record.get("price"), 120000); assert.equal(record.get("shippingAddress.recipient"), "buyer");
  assert.equal((await ok("getCheckoutQuote", { postId }, buyer)).existingOrderId, first.orderId);
});

test("buyer/seller paid lifecycle writes real timeline, shipment and money history", async () => {
  const seller = await account("seller-flow"), buyer = await account("buyer-flow"), outsider = await account("outsider");
  const { orderId, postId } = await order(seller, buyer);
  await denied("updateOrderStatus", { orderId, status: "PREPARING" }, seller);
  await denied("simulatePaymentResult", { orderId, status: "PAID" }, seller);
  await ok("simulatePaymentResult", { orderId, status: "PAID" }, buyer);
  await ok("simulatePaymentResult", { orderId, status: "PAID" }, buyer);
  assert.equal((await db.doc(`moneyTransactions/received_${orderId}`).get()).get("status"), "HOLDING");
  await denied("updateOrderStatus", { orderId, status: "PREPARING" }, buyer);
  for (const status of ["PREPARING", "READY_TO_SHIP"]) await ok("updateOrderStatus", { orderId, status }, seller);
  await denied("createShipment", { orderId, carrier: "REAL_CARRIER" }, seller);
  await ok("createShipment", { orderId }, seller); await ok("createShipment", { orderId }, seller);
  await denied("simulateShipmentUpdate", { orderId, status: "IN_TRANSIT", location: { lat: 91, lng: 10 } }, seller);
  await denied("simulateShipmentUpdate", { orderId, status: "DELIVERED" }, buyer);
  await ok("simulateShipmentUpdate", { orderId, status: "IN_TRANSIT", location: { lat: 10.77, lng: 106.7 }, description: "Điểm quét kiểm thử" }, seller);
  await ok("simulateShipmentUpdate", { orderId, status: "DELIVERED", description: "Đã giao mô phỏng" }, seller);
  await denied("updateOrderStatus", { orderId, status: "COMPLETED" }, seller);
  await ok("updateOrderStatus", { orderId, status: "COMPLETED" }, buyer);
  const shipment = await db.doc(`shipments/${orderId}`).get(); assert.equal(shipment.get("location.lat"), 10.77); assert.equal(shipment.get("events").length, 3);
  assert.equal((await db.doc(`orders/${orderId}`).get()).get("orderStatus"), "COMPLETED");
  assert.equal((await db.doc(`posts/${postId}`).get()).get("status"), "SOLD");
  assert.equal((await db.doc(`moneyTransactions/received_${orderId}`).get()).get("status"), "RELEASED");
  assert.equal((await db.doc(`moneyTransactions/payout_${orderId}`).get()).get("amount"), 120000);
  const events = await db.collection(`orders/${orderId}/statusEvents`).orderBy("createdAt").get();
  assert.deepEqual(events.docs.map(doc => doc.get("to")), ["PENDING_PAYMENT", "CONFIRMED", "PREPARING", "READY_TO_SHIP", "SHIPPING", "SHIPPING", "DELIVERED", "COMPLETED"]);
  assert.equal((await read(`shipments/${orderId}`, buyer)).status, 200); assert.equal((await read(`shipments/${orderId}`, outsider)).status, 403);
  assert.equal((await read(`moneyTransactions/received_${orderId}`, seller)).status, 200); assert.equal((await read(`moneyTransactions/received_${orderId}`, outsider)).status, 403);
});

test('shipment endpoints snapshot the default sender and order recipient without exposing contacts', async () => {
  const seller = await account('tracking-seller'), buyer = await account('tracking-buyer');
  const pickup = { recipient: 'Private sender', phone: '0909876543', street: '59C Nguyễn Đình Chiểu', ward: 'Võ Thị Sáu', district: 'Quận 3', province: 'TP.HCM', isDefault: true };
  await db.doc(`users/${seller.uid}/addresses/work`).set(pickup);
  const { orderId } = await paid(seller, buyer);
  for (const status of ['PREPARING', 'READY_TO_SHIP']) await ok('updateOrderStatus', { orderId, status }, seller);
  await ok('createShipment', { orderId, pickupAddress: { street: 'Spoofed address' } }, seller);
  const ref = db.doc(`shipments/${orderId}`), initial = (await ref.get()).data();
  assert.deepEqual(initial.pickupAddress, { street: pickup.street, ward: pickup.ward, district: pickup.district, province: pickup.province });
  assert.equal(initial.deliveryAddress.street, '1 Đường kiểm thử');
  for (const endpoint of [initial.pickupAddress, initial.deliveryAddress]) {
    assert.equal(endpoint.phone, undefined); assert.equal(endpoint.recipient, undefined);
  }
  await db.doc(`users/${seller.uid}/addresses/work`).update({ street: 'Changed after pickup' });
  await db.doc(`users/${buyer.uid}/addresses/home`).update({ street: 'Changed after checkout' });
  await ok('createShipment', { orderId }, seller);
  assert.deepEqual((await ref.get()).get('pickupAddress'), initial.pickupAddress);
  assert.deepEqual((await ref.get()).get('deliveryAddress'), initial.deliveryAddress);
  assert.equal((await read(`shipments/${orderId}`, buyer)).status, 200);

  const legacySeller = await account('tracking-no-address'), secondBuyer = await account('tracking-legacy-buyer');
  await db.doc(`users/${legacySeller.uid}/addresses/home`).delete();
  const next = await paid(legacySeller, secondBuyer);
  for (const status of ['PREPARING', 'READY_TO_SHIP']) await ok('updateOrderStatus', { orderId: next.orderId, status }, legacySeller);
  await ok('createShipment', { orderId: next.orderId }, legacySeller);
  assert.equal((await db.doc(`shipments/${next.orderId}`).get()).get('pickupAddress'), null);
});

test("rejection, buyer cancellation, payment failure and return release the correct reservation", async () => {
  const seller = await account("seller-cancel"), buyer = await account("buyer-cancel");
  for (const method of ["cancelOrder", "rejectOrder"]) {
    const { orderId, postId } = await paid(seller, buyer);
    await denied(method, { orderId }, method === "cancelOrder" ? seller : buyer);
    await ok(method, { orderId, reason: "Không thể giao trong thời gian yêu cầu" }, method === "cancelOrder" ? buyer : seller);
    assert.equal((await db.doc(`orders/${orderId}`).get()).get("paymentStatus"), "REFUNDED");
    assert.equal((await db.doc(`posts/${postId}`).get()).get("status"), "ACTIVE");
    assert.equal((await db.doc(`refunds/${orderId}`).get()).get("status"), "COMPLETED");
  }
  const failed = await order(seller, buyer); await ok("simulatePaymentResult", { orderId: failed.orderId, status: "FAILED" }, buyer);
  assert.equal((await db.doc(`posts/${failed.postId}`).get()).get("status"), "ACTIVE");
  const returned = await shipping(seller, buyer); await ok("simulateShipmentUpdate", { orderId: returned.orderId, status: "RETURNED", description: "Không thể giao" }, seller);
  assert.equal((await db.doc(`orders/${returned.orderId}`).get()).get("orderStatus"), "REFUNDED");
  assert.equal((await db.doc(`moneyTransactions/refund_${returned.orderId}`).get()).get("amount"), 135000);
});

test("all free requests are queryable and only one concurrent acceptance can create an order", async () => {
  const seller = await account("giver"), first = await account("requester-one"), second = await account("requester-two"), third = await account("requester-three");
  const postId = await listing(seller, "FREE");
  const requests = await Promise.all([first, second, third].map(user => ok("createFreeRequest", { postId, addressId: "home", note: "Mình muốn nhận món đồ này" }, user)));
  const all = await db.collection("freeRequests").where("ownerId", "==", seller.uid).get(); assert.equal(all.size, 3);
  const decisions = await Promise.all(requests.slice(0, 2).map(request => call("respondFreeRequest", { requestId: request.requestId, accept: true }, seller)));
  assert.equal(decisions.filter(result => result.status === 200).length, 1);
  const records = await db.collection("freeRequests").where("postId", "==", postId).get();
  assert.equal(records.docs.filter(doc => doc.get("status") === "ACCEPTED").length, 1);
  assert.equal(records.docs.filter(doc => doc.get("status") === "UNAVAILABLE").length, 2);
  const orders = await db.collection("orders").where("postId", "==", postId).get(); assert.equal(orders.size, 1); assert.equal(orders.docs[0].get("shippingAddress.recipient").startsWith("requester"), true);
  const another = await listing(seller, "FREE"); const request = await ok("createFreeRequest", { postId: another, addressId: "home", note: "Nhận đồ" }, first);
  await denied("cancelFreeRequest", { requestId: request.requestId }, seller);
  await ok("cancelFreeRequest", { requestId: request.requestId }, first);
  assert.equal((await db.doc(`freeRequests/${request.requestId}`).get()).get("status"), "CANCELLED");
});

test("private evidence is uploaded, locked after submission and an open dispute blocks payout", async () => {
  const seller = await account("seller-dispute"), buyer = await account("buyer-dispute"), outsider = await account("outside-dispute");
  const { orderId } = await shipping(seller, buyer); const uploadKey = "case-one";
  const path = `disputeEvidence/${orderId}/${buyer.uid}/${uploadKey}/0.jpg`;
  const forbidden = await upload(path, outsider); assert.equal(forbidden.status, 403);
  const invalidType = await upload(path, buyer, "text/plain"); assert.equal(invalidType.status, 403);
  const uploaded = await upload(path, buyer); assert.equal(uploaded.status, 200, await uploaded.text());
  const payload = { orderId, reason: "DAMAGED", description: "Sản phẩm bị hư hỏng khi nhận, cần kiểm tra bằng chứng.", uploadKey, evidencePaths: [path] };
  await denied("createDispute", { ...payload, evidencePaths: ["users/private/avatar.jpg"] }, buyer);
  await denied("createDispute", payload, outsider);
  await ok("createDispute", payload, buyer); await ok("createDispute", payload, buyer);
  assert.equal((await db.doc(`disputes/${orderId}`).get()).get("evidencePaths")[0], path);
  assert.equal((await read(`disputes/${orderId}`, seller)).status, 200); assert.equal((await read(`disputes/${orderId}`, outsider)).status, 403);
  const overwrite = await upload(path, buyer); assert.equal(overwrite.status, 403);
  await ok("simulateShipmentUpdate", { orderId, status: "DELIVERED", description: "Đã giao" }, seller);
  await denied("updateOrderStatus", { orderId, status: "COMPLETED" }, buyer);
  assert.equal((await db.doc(`moneyTransactions/received_${orderId}`).get()).get("status"), "HOLDING");
});

test("server deadlines expire pending orders and refund unconfirmed orders on Emulator", async () => {
  const seller = await account("deadline-seller"), buyer = await account("deadline-buyer"), outsider = await account("deadline-other");
  const pending = await order(seller, buyer);
  await denied("refreshOrder", { orderId: pending.orderId }, outsider);
  await ok("refreshOrder", { orderId: pending.orderId }, buyer);
  assert.equal((await db.doc(`orders/${pending.orderId}`).get()).get("orderStatus"), "PENDING_PAYMENT");
  await db.doc(`orders/${pending.orderId}`).update({ paymentDeadline: Timestamp.fromMillis(Date.now() - 1000) });
  await ok("refreshOrder", { orderId: pending.orderId }, buyer);
  assert.equal((await db.doc(`orders/${pending.orderId}`).get()).get("paymentStatus"), "EXPIRED");
  assert.equal((await db.doc(`posts/${pending.postId}`).get()).get("status"), "ACTIVE");
  const unconfirmed = await paid(seller, buyer);
  await db.doc(`orders/${unconfirmed.orderId}`).update({ sellerResponseDeadline: Timestamp.fromMillis(Date.now() - 1000) });
  await ok("refreshOrder", { orderId: unconfirmed.orderId }, seller);
  assert.equal((await db.doc(`orders/${unconfirmed.orderId}`).get()).get("paymentStatus"), "REFUNDED");
  assert.equal((await db.doc(`moneyTransactions/received_${unconfirmed.orderId}`).get()).get("status"), "REFUNDED");
});

test("settlement verifies delivery/payment, is idempotent and honors the three-day inspection deadline", async () => {
  const seller = await account("settlement-seller"), buyer = await account("settlement-buyer");
  const first = await shipping(seller, buyer);
  await denied("updateOrderStatus", { orderId: first.orderId, status: "COMPLETED" }, buyer);
  await ok("simulateShipmentUpdate", { orderId: first.orderId, status: "DELIVERED" }, seller);
  const delivered = await db.doc(`orders/${first.orderId}`).get();
  assert.ok(delivered.get("inspectionDeadline").toMillis() - delivered.get("deliveredAt").toMillis() >= 3 * 86400000 - 10000);
  await ok("refreshOrder", { orderId: first.orderId }, seller);
  assert.equal((await db.doc(`orders/${first.orderId}`).get()).get("orderStatus"), "DELIVERED");
  await denied("updateOrderStatus", { orderId: first.orderId, status: "COMPLETED" }, seller);
  await db.doc(`orders/${first.orderId}`).update({ inspectionDeadline: Timestamp.fromMillis(Date.now() - 1000) });
  await Promise.all([ok("refreshOrder", { orderId: first.orderId }, buyer), ok("refreshOrder", { orderId: first.orderId }, seller)]);
  assert.equal((await db.doc(`orders/${first.orderId}`).get()).get("orderStatus"), "COMPLETED");
  const payout = await db.doc(`moneyTransactions/payout_${first.orderId}`).get();
  assert.equal(payout.get("status"), "PAYOUT_COMPLETED_SIMULATED");
  await ok("updateOrderStatus", { orderId: first.orderId, status: "COMPLETED" }, buyer);
  assert.ok(payout.get("createdAt").isEqual((await db.doc(`moneyTransactions/payout_${first.orderId}`).get()).get("createdAt")));

  const blocked = await shipping(seller, buyer);
  await ok("simulateShipmentUpdate", { orderId: blocked.orderId, status: "DELIVERED" }, seller);
  await db.doc(`disputes/${blocked.orderId}`).set({ orderId: blocked.orderId, status: "OPEN" });
  await db.doc(`orders/${blocked.orderId}`).update({ inspectionDeadline: Timestamp.fromMillis(Date.now() - 1000) });
  await ok("refreshOrder", { orderId: blocked.orderId }, buyer);
  assert.equal((await db.doc(`orders/${blocked.orderId}`).get()).get("orderStatus"), "DELIVERED");
  assert.equal((await db.doc(`moneyTransactions/payout_${blocked.orderId}`).get()).exists, false);
  await denied("updateOrderStatus", { orderId: blocked.orderId, status: "COMPLETED" }, buyer);

  const unverified = await shipping(seller, buyer);
  await ok("simulateShipmentUpdate", { orderId: unverified.orderId, status: "DELIVERED" }, seller);
  const order = await db.doc(`orders/${unverified.orderId}`).get();
  await db.doc(`payments/${order.get("paymentId")}`).update({ status: "FAILED" });
  await denied("updateOrderStatus", { orderId: unverified.orderId, status: "COMPLETED" }, buyer);
  assert.equal((await db.doc(`moneyTransactions/payout_${unverified.orderId}`).get()).exists, false);
});
async function cart(buyer, postIds) {
  const batch = db.batch(); for (const postId of postIds) batch.set(db.doc(`users/${buyer.uid}/cartItems/${postId}`), { postId, addedAt: Timestamp.now() }); await batch.commit();
}
test('returned free shipment closes its accepted request and lets the buyer request again', async () => {
  const seller = await account('free-return-seller'), buyer = await account('free-return-buyer');
  const postId = await listing(seller, 'FREE');
  const { requestId } = await ok('createFreeRequest', { postId, addressId: 'home', note: 'Xin nhận món đồ' }, buyer);
  const { orderId } = await ok('respondFreeRequest', { requestId, accept: true }, seller);
  for (const status of ['PREPARING', 'READY_TO_SHIP']) await ok('updateOrderStatus', { orderId, status }, seller);
  await ok('createShipment', { orderId }, seller);
  await ok('simulateShipmentUpdate', { orderId, status: 'RETURNED' }, seller);
  assert.equal((await db.doc(`orders/${orderId}`).get()).get('orderStatus'), 'CANCELLED');
  assert.equal((await db.doc(`posts/${postId}`).get()).get('status'), 'ACTIVE');
  assert.equal((await db.doc(`freeRequests/${requestId}`).get()).get('status'), 'CANCELLED');
  const retried = await ok('createFreeRequest', { postId, addressId: 'home', note: 'Xin nhận lại' }, buyer);
  assert.equal(retried.requestId, requestId);
  const request = await db.doc(`freeRequests/${requestId}`).get();
  assert.equal(request.get('status'), 'PENDING'); assert.equal(request.get('orderId'), undefined);
  const next = await ok('respondFreeRequest', { requestId, accept: true }, seller);
  assert.notEqual(next.orderId, orderId);
});
async function grouped(seller, buyer, count = 2) {
  const postIds = []; for (let i=0;i<count;i++) postIds.push(await listing(seller)); await cart(buyer,postIds);
  const quote = await ok('getCartCheckoutQuote',{postIds},buyer);
  const result = await ok('createCartOrders',{postIds,addressId:'home',idempotencyKey:`group-${postIds[0]}`,expectedTotal:quote.total},buyer);
  return {postIds,orderId:result.orderIds[0]};
}
test('cart checkout groups by seller, snapshots every item, prices on server and atomically deduplicates', async () => {
  const seller = await account('group-seller'), secondSeller = await account('second-seller'), buyer = await account('group-buyer');
  const postIds = [await listing(seller),await listing(seller),await listing(secondSeller)], untouched = await listing(seller);
  await cart(buyer,[...postIds,untouched]); const quote = await ok('getCartCheckoutQuote',{postIds},buyer);
  assert.equal(quote.groups.length,2); assert.equal(quote.price,360000); assert.equal(quote.groups.find(g=>g.sellerId===seller.uid).items.length,2);
  const data = {postIds,addressId:'home',idempotencyKey:`multi-${postIds[0]}`,expectedTotal:quote.total};
  await denied('createCartOrders',{...data,expectedTotal:1},buyer);
  const [first,retry] = await Promise.all([ok('createCartOrders',data,buyer),ok('createCartOrders',data,buyer)]);
  assert.deepEqual(first.orderIds,retry.orderIds); assert.equal(first.orderIds.length,2);
  let itemCount=0;
  for (const id of first.orderIds) {
    const order = await db.doc(`orders/${id}`).get(); const items = order.get('items'); itemCount+=items.length;
    assert.equal(order.get('price'),items.reduce((sum,item)=>sum+item.price,0));
    assert.equal(order.get('shippingAddress.recipient'),'group-buyer');
    for (const item of items) { assert.equal(item.quantity,1); assert.ok(item.title); assert.equal((await db.doc(`posts/${item.postId}`).get()).get('reservedForOrderId'),id); assert.equal((await db.doc(`users/${buyer.uid}/cartItems/${item.postId}`).get()).exists,false); }
  }
  assert.equal(itemCount,3); assert.equal((await db.doc(`users/${buyer.uid}/cartItems/${untouched}`).get()).exists,true);
  await denied('createCartOrders',{...data,postIds:[untouched]},buyer);
});
test('concurrent multi-item buyers cannot reserve the same unique garment or create partial orders', async () => {
  const seller=await account('race-seller'), one=await account('race-one'), two=await account('race-two');
  const shared=await listing(seller), extraOne=await listing(seller), extraTwo=await listing(seller);
  await cart(one,[shared,extraOne]); await cart(two,[shared,extraTwo]);
  const q1=await ok('getCartCheckoutQuote',{postIds:[shared,extraOne]},one), q2=await ok('getCartCheckoutQuote',{postIds:[shared,extraTwo]},two);
  const results=await Promise.all([call('createCartOrders',{postIds:[shared,extraOne],addressId:'home',idempotencyKey:'race-one',expectedTotal:q1.total},one),call('createCartOrders',{postIds:[shared,extraTwo],addressId:'home',idempotencyKey:'race-two',expectedTotal:q2.total},two)]);
  assert.equal(results.filter(result=>result.status===200).length,1);
  const loser=results[0].status===200?extraTwo:extraOne;
  assert.equal((await db.doc(`posts/${loser}`).get()).get('status'),'ACTIVE');
  assert.equal((await db.collection('orders').where('buyerId','==',results[0].status===200?two.uid:one.uid).get()).size,0);
});
test('every item is released on cancellation, failure, rejection, return and deadline; payout uses combined subtotal', async () => {
  const seller=await account('multi-life-seller'), buyer=await account('multi-life-buyer');
  for (const operation of ['cancelOrder','rejectOrder','FAILED','RETURNED','EXPIRED','SETTLE']) {
    const {postIds,orderId}=await grouped(seller,buyer);
    if (operation==='EXPIRED') { await db.doc(`orders/${orderId}`).update({paymentDeadline:Timestamp.fromMillis(Date.now()-1000)}); await ok('refreshOrder',{orderId},buyer); }
    else if (operation==='FAILED') await ok('simulatePaymentResult',{orderId,status:'FAILED'},buyer);
    else {
      await ok('simulatePaymentResult',{orderId,status:'PAID'},buyer);
      for (const postId of postIds) assert.equal((await db.doc(`posts/${postId}`).get()).get('status'),'SOLD');
      if (['cancelOrder','rejectOrder'].includes(operation)) await ok(operation,{orderId},operation==='cancelOrder'?buyer:seller);
      else {
        for (const status of ['PREPARING','READY_TO_SHIP']) await ok('updateOrderStatus',{orderId,status},seller);
        await ok('createShipment',{orderId},seller);
        await ok('simulateShipmentUpdate',{orderId,status:operation==='RETURNED'?'RETURNED':'DELIVERED'},seller);
        if (operation==='SETTLE') { await ok('updateOrderStatus',{orderId,status:'COMPLETED'},buyer); assert.equal((await db.doc(`moneyTransactions/payout_${orderId}`).get()).get('amount'),240000); }
      }
    }
    for (const postId of postIds) assert.equal((await db.doc(`posts/${postId}`).get()).get('status'),operation==='SETTLE'?'SOLD':'ACTIVE',operation);
  }
});
test('admin dispute resolution updates every item in a grouped order', async () => {
  const admin=await account('multi-admin'), seller=await account('multi-admin-seller'), buyer=await account('multi-admin-buyer');
  await require('firebase-admin/auth').getAuth(app).setCustomUserClaims(admin.uid,{admin:true});
  await db.doc(`users/${admin.uid}`).update({role:'ADMIN'});
  for (const resolution of ['REFUND','RELEASE']) {
    const {postIds,orderId}=await grouped(seller,buyer);
    await ok('simulatePaymentResult',{orderId,status:'PAID'},buyer);
    for (const status of ['PREPARING','READY_TO_SHIP']) await ok('updateOrderStatus',{orderId,status},seller);
    await ok('createShipment',{orderId},seller);
    await ok('createDispute',{orderId,reason:'DAMAGED',description:'Món đồ bị hư hỏng, cần quản trị kiểm tra.',uploadKey:'multi-case',evidencePaths:[]},buyer);
    const data={action:'disputeResolve',id:orderId,value:{resolution},note:'Đã xem xét các món trong đơn.',requestId:`multi-resolution-${orderId}`};
    await ok('adminMutate',data,admin); await ok('adminMutate',data,admin);
    for (const postId of postIds) assert.equal((await db.doc(`posts/${postId}`).get()).get('status'),resolution==='REFUND'?'HIDDEN':'SOLD');
    assert.equal((await db.doc(`moneyTransactions/${resolution==='REFUND'?'refund':'payout'}_${orderId}`).get()).get('amount'),resolution==='REFUND'?240000+(await db.doc(`orders/${orderId}`).get()).get('shippingFee'):240000);
  }
});
