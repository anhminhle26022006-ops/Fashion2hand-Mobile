const crypto = require("node:crypto");
const { initializeApp } = require("firebase-admin/app");
const { getStorage } = require("firebase-admin/storage");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { onCall: firebaseOnCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");

initializeApp();
const db = getFirestore();
const orderItems = require("./order-items");
const readOrderPosts = (tx, order) => orderItems.readOrderPosts(tx, order, db);
const writeOrderPosts = orderItems.writeOrderPosts;
const { getAuth } = require("firebase-admin/auth");
const paymentWebhookToken = defineSecret("PAYMENT_WEBHOOK_TOKEN");
const shipmentWebhookToken = defineSecret("SHIPMENT_WEBHOOK_TOKEN");

const fail = (code, message, details) => { throw new HttpsError(code, message, details); };
const settleDelivery = require("./escrow")({ db, FieldValue, fail, writeOrderEvent });
// Check the current server profile on every callable, including tokens issued before a lock.
function onCall(options, handler) {
  if (typeof options === "function") { handler = options; options = {}; }
  return firebaseOnCall(options, async request => {
    if (request.auth) {
      const profile = await db.doc(`users/${request.auth.uid}`).get();
      if (profile.exists && profile.get("accountStatus") !== "ACTIVE") fail("permission-denied", "Tài khoản đã bị khóa.");
    }
    return handler(request);
  });
}
function uidOf(request) {
  if (!request.auth) fail("unauthenticated", "Vui lòng đăng nhập.");
  return request.auth.uid;
}
async function requireVerifiedAccount(request) {
  const uid = uidOf(request);
  const user = await getAuth().getUser(uid);
  if (!user.disabled && user.customClaims?.admin === true) {
    const profile = await db.doc(`users/${uid}`).get();
    if (profile.exists && profile.get("role") === "ADMIN" && profile.get("accountStatus") === "ACTIVE") return uid;
  }
  // Phone verification is optional while paid SMS is disabled.
  if (!user.email || !user.emailVerified || user.disabled)
    fail("failed-precondition", "Bạn cần xác minh email trước khi đăng bài, mua hàng hoặc xin đồ.");
  return uid;
}
function cleanText(value, max, label, required = true) {
  const result = typeof value === "string" ? value.trim() : "";
  if (required && !result) fail("invalid-argument", `${label} không được để trống.`);
  if (result.length > max) fail("invalid-argument", `${label} vượt quá ${max} ký tự.`);
  return result;
}
function cleanDocumentId(value, label) {
  const result = cleanText(value, 128, label);
  if (result.includes("/") || result === "." || result === "..") fail("invalid-argument", `${label} không hợp lệ.`);
  return result;
}

function simulationEnabled(kind) {
  return process.env.FUNCTIONS_EMULATOR === "true" && process.env[`ENABLE_SIMULATED_${kind}`] === "true";
}
function validatedAddress(address) {
  if (!address || !address.exists) fail("failed-precondition", "Vui lòng chọn địa chỉ nhận hàng đã lưu.");
  const value = addressSnapshot(address);
  if (value.addressVersion || value.provinceCode || value.wardCode)
    Object.assign(value, require("./address-catalog").canonicalAddress(value, fail));
  if (!value.recipient.trim() || !value.province.trim() || !value.ward.trim()
      || !value.street.trim() || !/^\+?[0-9 ()-]{9,20}$/.test(value.phone))
    fail("failed-precondition", "Địa chỉ nhận hàng chưa đầy đủ hoặc số điện thoại không hợp lệ.");
  return value;
}
function shipmentLocation(value) {
  if (value == null) return null;
  if (typeof value.lat !== "number" || typeof value.lng !== "number" || !Number.isFinite(value.lat)
      || !Number.isFinite(value.lng) || Math.abs(value.lat) > 90 || Math.abs(value.lng) > 180)
    fail("invalid-argument", "Tọa độ vận chuyển không hợp lệ.");
  return { lat: value.lat, lng: value.lng, recordedAt: new Date().toISOString() };
}

exports.getTransactionCapabilities = onCall(async request => {
  uidOf(request);
  return { simulatedPayments: simulationEnabled("PAYMENTS"), simulatedShipping: simulationEnabled("SHIPPING"),
    checkoutItemsPerOrder: 20, checkoutGrouping: "SELLER", shippingFee: await shippingFeeVnd(), liveProvidersConnected: false };
});

exports.getCheckoutQuote = onCall(async request => {
  const uid = uidOf(request);
  const postId = cleanDocumentId(request.data.postId, "Bài đăng");
  const post = await db.doc(`posts/${postId}`).get();
  if (!post.exists || !["SALE", "FREE"].includes(postType(post))) fail("not-found", "Không tìm thấy món đồ.");
  if (postOwnerId(post) === uid) fail("failed-precondition", "Không thể nhận hoặc mua món đồ của chính bạn.");
  if (post.get("status") !== "ACTIVE" || post.get("reservedForOrderId")) {
    const reserved = post.get("reservedForOrderId");
    const order = reserved ? await db.doc(`orders/${reserved}`).get() : null;
    if (order?.exists && order.get("buyerId") === uid && order.get("orderStatus") === "PENDING_PAYMENT")
      return { existingOrderId: order.id };
    fail("failed-precondition", "Món đồ không còn khả dụng.");
  }
  const price = postType(post) === "FREE" ? 0 : post.get("price");
  if (!Number.isSafeInteger(price) || price < 0 || (postType(post) === "SALE" && price === 0))
    fail("failed-precondition", "Giá bài đăng không hợp lệ.");
  const shippingFee = await shippingFeeVnd();
  return { postId, sellerId: postOwnerId(post), title: post.get("title") || "", type: postType(post),
    price, shippingFee, total: price + shippingFee, currency: "VND", mode: "SIMULATED" };
});

// Emulator does not execute scheduled jobs. Opening an order also reconciles
// deadlines using server time; clients cannot force an unexpired order to expire.
exports.refreshOrder = onCall(async request => {
  const uid = uidOf(request), orderId = cleanDocumentId(request.data.orderId, "Đơn hàng");
  const ref = db.doc(`orders/${orderId}`);
  await db.runTransaction(async tx => {
    const order = await tx.get(ref);
    const orderPosts = await readOrderPosts(tx, order);
    if (!order.exists || ![order.get("buyerId"), order.get("sellerId")].includes(uid)) fail("permission-denied", "Không thể xem đơn hàng này.");
    const now = new Date();
    const unpaid = order.get("orderStatus") === "PENDING_PAYMENT" && order.get("paymentDeadline")?.toDate() <= now;
    const unconfirmed = order.get("orderStatus") === "CONFIRMED" && order.get("sellerResponseDeadline")?.toDate() <= now;
    if (!unpaid && !unconfirmed) return;
    const postRef = db.doc(`posts/${order.get("postId")}`), post = await tx.get(postRef);
    const paymentRef = order.get("paymentId") ? db.doc(`payments/${order.get("paymentId")}`) : null;
    const payment = paymentRef ? await tx.get(paymentRef) : null;
    const next = unconfirmed ? "REFUNDED" : "CANCELLED";
    tx.update(ref, { orderStatus: next, paymentStatus: unconfirmed ? "REFUNDED" : "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
    if (post.exists && post.get("reservedForOrderId") === orderId) writeOrderPosts(tx, orderPosts, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
    if (payment?.exists) tx.update(paymentRef, { status: unconfirmed ? "REFUNDED" : "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
    if (unconfirmed && payment?.exists) {
      tx.update(db.doc(`moneyTransactions/received_${orderId}`), { status: "REFUNDED", refundedAt: FieldValue.serverTimestamp() });
      tx.set(db.doc(`refunds/${orderId}`), { orderId, paymentId: paymentRef.id, amount: payment.get("amount"), status: "COMPLETED", mode: "SIMULATED", reason: "Người bán quá hạn phản hồi", createdAt: FieldValue.serverTimestamp() });
      tx.set(db.doc(`moneyTransactions/refund_${orderId}`), { orderId, paymentId: paymentRef.id, type: "REFUND", amount: payment.get("amount"), status: "COMPLETED", mode: "SIMULATED", createdAt: FieldValue.serverTimestamp() });
    }
    writeOrderEvent(tx, ref, order.get("orderStatus"), next, "SYSTEM", unpaid ? "Quá hạn thanh toán 15 phút" : "Người bán quá hạn phản hồi 48 giờ");
  });
  await settleDelivery(orderId);
  return { ok: true };
});

exports.createDispute = onCall(async request => {
  const uid = uidOf(request);
  const orderId = cleanDocumentId(request.data.orderId, "Đơn hàng");
  const reason = cleanText(request.data.reason, 40, "Lý do");
  if (!["NOT_RECEIVED", "DAMAGED", "MISMATCH", "WRONG_ITEM", "OTHER"].includes(reason)) fail("invalid-argument", "Lý do không hợp lệ.");
  const description = cleanText(request.data.description, 2000, "Mô tả");
  if (description.length < 10) fail("invalid-argument", "Mô tả cần ít nhất 10 ký tự.");
  const uploadKey = cleanDocumentId(request.data.uploadKey, "Mã bằng chứng");
  const evidence = request.data.evidencePaths || [];
  if (!Array.isArray(evidence) || evidence.length > 5 || new Set(evidence).size !== evidence.length)
    fail("invalid-argument", "Mỗi tranh chấp tối đa 5 ảnh khác nhau.");
  const orderRef = db.doc(`orders/${orderId}`);
  const disputeRef = db.doc(`disputes/${orderId}`);
  const initial = await orderRef.get();
  if (!initial.exists || ![initial.get("buyerId"), initial.get("sellerId")].includes(uid)) fail("permission-denied", "Bạn không thuộc đơn hàng này.");
  const existing = await disputeRef.get();
  if (existing.exists) {
    if (existing.get("reporterId") === uid && existing.get("uploadKey") === uploadKey) return { disputeId: disputeRef.id };
    fail("already-exists", "Đơn hàng đã có tranh chấp.");
  }
  if (!["SHIPPING", "DELIVERED"].includes(initial.get("orderStatus"))) fail("failed-precondition", "Chỉ báo cáo khi đang giao hoặc đã giao, trước khi xác nhận hoàn tất.");
  const bucket = getStorage().bucket(process.env.EVIDENCE_BUCKET || "fashion2hand.firebasestorage.app");
  const prefix = `disputeEvidence/${orderId}/${uid}/${uploadKey}/`;
  await Promise.all(evidence.map(async path => {
    if (typeof path !== "string" || !path.startsWith(prefix) || !/^[0-4]\.jpg$/.test(path.slice(prefix.length)))
      fail("invalid-argument", "Đường dẫn bằng chứng không hợp lệ.");
    let metadata;
    try { [metadata] = await bucket.file(path).getMetadata(); }
    catch { fail("failed-precondition", "Ảnh bằng chứng chưa được tải lên đầy đủ."); }
    if (!metadata.contentType?.startsWith("image/") || Number(metadata.size) >= 5 * 1024 * 1024)
      fail("invalid-argument", "Ảnh bằng chứng không hợp lệ hoặc vượt quá 5 MB.");
  }));
  await db.runTransaction(async tx => {
    const [order, prior] = await Promise.all([tx.get(orderRef), tx.get(disputeRef)]);
    if (prior.exists) {
      if (prior.get("reporterId") === uid && prior.get("uploadKey") === uploadKey) return;
      fail("already-exists", "Đơn hàng đã có tranh chấp.");
    }
    if (!order.exists || ![order.get("buyerId"), order.get("sellerId")].includes(uid)
        || !["SHIPPING", "DELIVERED"].includes(order.get("orderStatus"))) fail("failed-precondition", "Đơn hàng không còn cho phép báo cáo.");
    if (order.get("inspectionDeadline")?.toMillis() <= Date.now())
      fail("failed-precondition", "Đã hết thời hạn mở tranh chấp 3 ngày sau khi giao.");
    tx.create(disputeRef, { orderId, buyerId: order.get("buyerId"), sellerId: order.get("sellerId"), reporterId: uid,
      reason, description, evidencePaths: evidence, evidenceBucket: bucket.name, uploadKey, status: "OPEN", createdAt: FieldValue.serverTimestamp() });
    tx.update(orderRef, { disputeId: disputeRef.id, disputeStatus: "OPEN", updatedAt: FieldValue.serverTimestamp() });
    writeOrderEvent(tx, orderRef, order.get("orderStatus"), order.get("orderStatus"), uid, "Đã mở tranh chấp; chờ xử lý trước khi hoàn tất đơn.");
  });
  const other = initial.get("buyerId") === uid ? initial.get("sellerId") : initial.get("buyerId");
  await notify(other, { type: "DISPUTE_OPENED", title: "Đơn hàng có tranh chấp", body: "Một bên đã gửi báo cáo và bằng chứng.", orderId });
  return { disputeId: disputeRef.id };
});

// Publish only explicitly public profile fields, never contact or identity data.
exports.getSellerProfile = onCall(async request => {
  uidOf(request);
  const sellerId = cleanDocumentId(request.data?.sellerId, "Người đăng");
  const user = await db.collection("users").doc(sellerId).get();
  if (!user.exists || user.get("accountStatus") !== "ACTIVE")
    fail("not-found", "Hồ sơ không tồn tại hoặc không còn hoạt động.");
  const publicString = field => typeof user.get(field) === "string" ? user.get(field) : "";
  return { uid: sellerId, name: publicString("name"), photoUrl: publicString("photoUrl"),
    region: publicString("region"), bio: publicString("bio") };
});

// Copy contact details from Auth, not from client-supplied verification flags.
exports.syncAccountContact = onCall(async request => {
  const uid = uidOf(request);
  const user = await getAuth().getUser(uid);
  const profile = db.collection("users").doc(uid);
  const update = { email: user.email || "", emailVerified: user.emailVerified,
    phone: user.phoneNumber || "", phoneVerified: Boolean(user.phoneNumber), updatedAt: FieldValue.serverTimestamp() };
  await profile.update(update);
  return { email: update.email, emailVerified: update.emailVerified, phone: update.phone, phoneVerified: update.phoneVerified };
});
function postOwnerId(post) { return post.get("ownerId") || post.get("userId") || ""; }
function postType(post) {
  const value = post.get("type") || post.get("listingType");
  if (value === "FREE") return "FREE";
  if (value === "SALE" || value === "FOR_SALE") return "SALE";
  return "";
}
function orderPostSnapshot(post) {
  return {
    title: cleanText(post.get("title"), 160, "Tiêu đề sản phẩm", false),
    category: cleanText(post.get("category") || post.get("categoryName"), 100, "Danh mục", false),
    size: cleanText(post.get("size"), 40, "Kích cỡ", false),
    condition: cleanText(post.get("condition"), 40, "Tình trạng", false),
    location: cleanText(post.get("location"), 180, "Vị trí", false)
  };
}
function addressSnapshot(address) {
  const data = address.data();
  return {
    recipient: String(data.recipient || data.recipientName || data.fullName || ""),
    phone: String(data.phone || ""),
    province: String(data.province || ""),
    district: String(data.district || ""),
    provinceCode: String(data.provinceCode || ""), wardCode: String(data.wardCode || ""),
    addressVersion: String(data.addressVersion || ""),
    ward: String(data.ward || ""),
    street: String(data.street || data.addressLine || ""),
    label: String(data.label || data.type || "")
  };
}
async function notify(uid, data) {
  try {
    return await db.collection("users").doc(uid).collection("notifications").add({
      ...data, read: false, createdAt: FieldValue.serverTimestamp()
    });
  } catch (error) {
    // Never report a committed order/message as failed only because its
    // follow-up notification could not be written; the client may retry it.
    logger.error("Notification write failed", { uid, type: data.type, error });
    return null;
  }
}
function writeOrderEvent(tx, orderRef, from, to, actorId, reason = "") {
  const eventRef = orderRef.collection("statusEvents").doc();
  tx.create(eventRef, { from, to, actorId, reason, createdAt: FieldValue.serverTimestamp() });
}
async function shippingFeeVnd() {
  const tariff = await db.collection("shippingTariffs").doc("active").get();
  return tariff.exists && Number.isSafeInteger(tariff.get("minFee"))
    ? Math.max(0, tariff.get("minFee")) : 15000;
}
function validPaymentWebhook(req) {
  const expected = paymentWebhookToken.value();
  const provided = (req.get("authorization") || "").replace(/^Bearer\s+/i, "");
  return safeTokenMatch(expected, provided);
}
function validShipmentWebhook(req) {
  const expected = shipmentWebhookToken.value();
  const provided = (req.get("authorization") || "").replace(/^Bearer\s+/i, "");
  return safeTokenMatch(expected, provided);
}
function safeTokenMatch(expected, provided) {
  if (!expected || !provided) return false;
  const a = Buffer.from(expected); const b = Buffer.from(provided);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

exports.createChat = onCall(async (request) => {
  const uid = uidOf(request);
  const otherUserId = cleanDocumentId(request.data.otherUserId, "Người nhận");
  const postId = request.data.postId ? cleanDocumentId(request.data.postId, "Bài đăng") : "";
  if (uid === otherUserId) fail("invalid-argument", "Không thể tự nhắn tin cho mình.");
  const otherUser = await db.collection("users").doc(otherUserId).get();
    if (!otherUser.exists || otherUser.get("accountStatus") === "LOCKED") fail("not-found", "Không tìm thấy thành viên này.");
  if (postId) {
    const post = await db.collection("posts").doc(postId).get();
    if (!post.exists || ![uid, otherUserId].includes(postOwnerId(post))) fail("failed-precondition", "Bài đăng không thuộc thành viên cuộc trò chuyện.");
  }
  const members = [uid, otherUserId].sort();
  const chatId = crypto.createHash("sha256").update(`${members.join(":")}:${postId}`).digest("hex");
  const ref = db.collection("chats").doc(chatId);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) tx.create(ref, {
      participantIds: members, postId: postId || null,
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
      lastMessage: "", lastMessageAt: null, unreadCounts: { [uid]: 0, [otherUserId]: 0 }
    });
    else if (!members.every((member) => snap.get("participantIds").includes(member))) {
      fail("permission-denied", "Cuộc trò chuyện không hợp lệ.");
    }
  });
  return { chatId };
});

exports.sendMessage = onCall(async (request) => {
  const uid = uidOf(request);
  const chatId = cleanDocumentId(request.data.chatId, "Cuộc trò chuyện");
  const text = cleanText(request.data.text, 4000, "Tin nhắn");
  const chatRef = db.collection("chats").doc(chatId);
  const clientMessageId = request.data.clientMessageId
    ? cleanDocumentId(request.data.clientMessageId, "Mã tin nhắn") : null;
  const messageRef = clientMessageId
    ? chatRef.collection("messages").doc(crypto.createHash("sha256").update(`${uid}:${clientMessageId}`).digest("hex"))
    : chatRef.collection("messages").doc();
  let recipientId;
  await db.runTransaction(async (tx) => {
    const chat = await tx.get(chatRef);
    const participants = chat.exists ? chat.get("participantIds") : null;
    if (!participants || !participants.includes(uid) || participants.length !== 2) {
      fail("permission-denied", "Bạn không thuộc cuộc trò chuyện này.");
    }
    recipientId = participants.find((id) => id !== uid);
    const existing = await tx.get(messageRef);
    if (existing.exists) {
      if (existing.get("senderId") !== uid || existing.get("text") !== text)
        fail("already-exists", "Mã tin nhắn đã được dùng cho nội dung khác.");
      return;
    }
    tx.create(messageRef, { senderId: uid, text, createdAt: FieldValue.serverTimestamp(), read: false });
    tx.update(chatRef, {
      lastMessage: text, lastMessageAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
      [`unreadCounts.${recipientId}`]: FieldValue.increment(1)
    });
    tx.create(db.collection("users").doc(recipientId).collection("notifications").doc(`message_${messageRef.id}`), {
      type: "MESSAGE", title: "Tin nhắn mới", body: text.slice(0, 120), chatId,
      read: false, createdAt: FieldValue.serverTimestamp()
    });
  });
  return { messageId: messageRef.id };
});

exports.markChatRead = onCall(async (request) => {
  const uid = uidOf(request);
  const chatId = cleanDocumentId(request.data.chatId, "Cuộc trò chuyện");
  const chatRef = db.collection("chats").doc(chatId);
  const chat = await chatRef.get();
  if (!chat.exists || !chat.get("participantIds").includes(uid)) fail("permission-denied", "Bạn không thuộc cuộc trò chuyện này.");
  let marked = 0;
  while (true) {
    let count = 0;
    await db.runTransaction(async (tx) => {
      const currentChat = await tx.get(chatRef);
      const unread = await tx.get(chatRef.collection("messages").where("read", "==", false)
        .where("senderId", "!=", uid).limit(450));
      if (unread.empty) {
        if (Number((currentChat.get("unreadCounts") || {})[uid] || 0) !== 0)
          tx.update(chatRef, { [`unreadCounts.${uid}`]: 0 });
        return;
      }
      const unreadCounts = currentChat.get("unreadCounts") || {};
      count = unread.size;
      unread.docs.forEach((message) => tx.update(message.ref, { read: true, readAt: FieldValue.serverTimestamp() }));
      tx.update(chatRef, { [`unreadCounts.${uid}`]: Math.max(0, Number(unreadCounts[uid] || 0) - count) });
    });
    marked += count;
    if (count < 450) break;
  }
  return { marked };
});

exports.createFreeRequest = onCall(async (request) => {
  await requireVerifiedAccount(request);
  const uid = uidOf(request);
  const postId = cleanDocumentId(request.data.postId, "Bài đăng");
  const addressId = request.data.addressId ? cleanDocumentId(request.data.addressId, "Địa chỉ nhận") : "";
  const note = cleanText(request.data.note, 500, "Lời nhắn", false);
  const ref = db.collection("posts").doc(postId);
  const requestId = crypto.createHash("sha256").update(`${uid}:${postId}`).digest("hex");
  const requestRef = db.collection("freeRequests").doc(requestId);
  const shippingFee = await shippingFeeVnd();
  const now = FieldValue.serverTimestamp();
  const addressRef = addressId ? db.collection("users").doc(uid).collection("addresses").doc(addressId) : null;
  let ownerId;
  await db.runTransaction(async (tx) => {
    const post = await tx.get(ref);
    if (!post.exists || postType(post) !== "FREE" || post.get("status") !== "ACTIVE") {
      fail("failed-precondition", "Món đồ miễn phí hiện không còn nhận yêu cầu.");
    }
    ownerId = postOwnerId(post);
    if (!ownerId) fail("failed-precondition", "Bài đăng chưa có chủ sở hữu hợp lệ.");
    if (ownerId === uid) fail("failed-precondition", "Không thể yêu cầu món đồ của chính bạn.");
    const pending = await tx.get(db.collection("freeRequests")
      .where("postId", "==", postId).where("requesterId", "==", uid).where("status", "==", "PENDING").limit(1));
    if (!pending.empty) fail("already-exists", "Bạn đã gửi yêu cầu cho món đồ này.");
    const prior = await tx.get(requestRef);
    if (prior.exists && !["REJECTED", "CANCELLED", "UNAVAILABLE"].includes(prior.get("status"))) fail("already-exists", "Bạn đã gửi yêu cầu cho món đồ này.");
    const address = addressRef ? await tx.get(addressRef) : null;
    if (addressRef && !address.exists) fail("not-found", "Không tìm thấy địa chỉ nhận hàng.");
    const shippingAddress = validatedAddress(address);
    if (prior.exists) {
      tx.update(requestRef, { note, postSnapshot: orderPostSnapshot(post), addressId: addressId || null, shippingAddress, status: "PENDING", createdAt: now, updatedAt: FieldValue.serverTimestamp() });
      return;
    }
    tx.create(requestRef, {
      postId, ownerId, requesterId: uid, postSnapshot: orderPostSnapshot(post), note, addressId: addressId || null, shippingAddress, status: "PENDING",
      createdAt: now, updatedAt: now
    });
  });
  await notify(ownerId, { type: "FREE_REQUEST", title: "Có yêu cầu nhận đồ", body: "Một thành viên muốn nhận món đồ miễn phí của bạn.", requestId: requestRef.id, postId });
  return { requestId: requestRef.id };
});

exports.respondFreeRequest = onCall(async (request) => {
  const uid = uidOf(request);
  const requestId = cleanDocumentId(request.data.requestId, "Yêu cầu");
  const accept = request.data.accept === true;
  const reqRef = db.collection("freeRequests").doc(requestId);
  const orderRef = db.collection("orders").doc();
  const shippingFee = accept ? await shippingFeeVnd() : 0;
  let requesterId; let postId; const unavailableRequesterIds = [];
  await db.runTransaction(async (tx) => {
    const req = await tx.get(reqRef);
    if (!req.exists || req.get("ownerId") !== uid || req.get("status") !== "PENDING") {
      fail("permission-denied", "Yêu cầu không tồn tại hoặc không thể xử lý.");
    }
    requesterId = req.get("requesterId");
    const postRef = db.collection("posts").doc(req.get("postId"));
    postId = postRef.id;
    const post = await tx.get(postRef);
    if (!post.exists || postOwnerId(post) !== uid || postType(post) !== "FREE") {
      fail("failed-precondition", "Bài đăng không còn hợp lệ.");
    }
    const next = accept ? "ACCEPTED" : "REJECTED";
    if (accept) {
      if (!req.get("shippingAddress")) fail("failed-precondition", "Yêu cầu chưa có địa chỉ nhận hàng. Người nhận cần gửi lại yêu cầu với địa chỉ đầy đủ.");
      if (post.get("status") !== "ACTIVE") fail("failed-precondition", "Món đồ đã được nhận bởi người khác hoặc đã ẩn.");
      const otherPendingRequests = await tx.get(db.collection("freeRequests")
        .where("postId", "==", postRef.id).where("status", "==", "PENDING").limit(450));
      tx.update(reqRef, { status: next, orderId: orderRef.id, updatedAt: FieldValue.serverTimestamp() });
      otherPendingRequests.docs.forEach((pending) => {
        if (pending.id !== requestId) {
          tx.update(pending.ref, { status: "UNAVAILABLE", updatedAt: FieldValue.serverTimestamp() });
          unavailableRequesterIds.push(pending.get("requesterId"));
        }
      });
      tx.update(postRef, { status: "RESERVED", reservedForOrderId: orderRef.id, updatedAt: FieldValue.serverTimestamp() });
      tx.create(orderRef, {
        orderType: "FREE", buyerId: requesterId, sellerId: uid, postId: postRef.id,
        requestId, price: 0, shippingFee,
        postSnapshot: orderPostSnapshot(post),
        shippingAddress: req.get("shippingAddress") || null,
        orderStatus: "CONFIRMED", paymentStatus: "NOT_REQUIRED", shippingStatus: "READY",
        trackingCode: "", createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp()
      });
      writeOrderEvent(tx, orderRef, "REQUEST_ACCEPTED", "CONFIRMED", uid, "Yêu cầu nhận món miễn phí được chấp nhận.");
    } else tx.update(reqRef, { status: next, updatedAt: FieldValue.serverTimestamp() });
  });
  await notify(requesterId, { type: accept ? "FREE_REQUEST_ACCEPTED" : "FREE_REQUEST_REJECTED", title: accept ? "Yêu cầu đã được chấp nhận" : "Yêu cầu chưa được chấp nhận", body: accept ? "Người tặng đã xác nhận yêu cầu của bạn." : "Người tặng đã từ chối yêu cầu.", requestId, ...(accept ? { orderId: orderRef.id } : {}) });
  await Promise.all(unavailableRequesterIds.map((targetUid) => notify(targetUid, {
    type: "FREE_REQUEST_UNAVAILABLE", title: "Món đồ đã có người nhận", body: "Người đăng đã chọn một yêu cầu khác cho món đồ này.", postId
  })));
  return accept ? { orderId: orderRef.id } : { ok: true };
});

exports.cancelFreeRequest = onCall(async (request) => {
  const uid = uidOf(request);
  const requestId = cleanDocumentId(request.data.requestId, "Yêu cầu");
  const requestRef = db.collection("freeRequests").doc(requestId);
  let ownerId;
  await db.runTransaction(async (tx) => {
    const req = await tx.get(requestRef);
    if (!req.exists || req.get("requesterId") !== uid || req.get("status") !== "PENDING") fail("failed-precondition", "Yêu cầu không thể hủy.");
    ownerId = req.get("ownerId");
    tx.update(requestRef, { status: "CANCELLED", updatedAt: FieldValue.serverTimestamp() });
  });
  await notify(ownerId, { type: "FREE_REQUEST_CANCELLED", title: "Yêu cầu đã được hủy", body: "Người nhận đã rút lại yêu cầu.", requestId });
  return { ok: true };
});

exports.createPaidOrder = onCall(async (request) => {
  await requireVerifiedAccount(request);
  const uid = uidOf(request);
  const postId = cleanDocumentId(request.data.postId, "Bài đăng");
  const idempotencyKey = cleanText(request.data.idempotencyKey, 128, "Mã yêu cầu");
  const addressId = request.data.addressId ? cleanDocumentId(request.data.addressId, "Địa chỉ nhận") : "";
  const shippingFee = await shippingFeeVnd();
  const idemId = crypto.createHash("sha256").update(`${uid}:${idempotencyKey}`).digest("hex");
  const idemRef = db.collection("orderRequests").doc(idemId);
  const postRef = db.collection("posts").doc(postId);
  const addressRef = addressId ? db.collection("users").doc(uid).collection("addresses").doc(addressId) : null;
  let orderId; let sellerId;
  await db.runTransaction(async (tx) => {
    const previous = await tx.get(idemRef);
    if (previous.exists) {
      if (previous.get("postId") && previous.get("postId") !== postId) fail("already-exists", "Mã yêu cầu này đã được dùng cho sản phẩm khác.");
      if (previous.get("addressId") && previous.get("addressId") !== addressId) fail("already-exists", "Đơn đã được tạo với địa chỉ trước đó. Hãy mở đơn đang chờ thanh toán.");
      orderId = previous.get("orderId"); return;
    }
    const post = await tx.get(postRef);
    if (!post.exists || postType(post) !== "SALE" || post.get("status") !== "ACTIVE" || post.get("reservedForOrderId")) {
      fail("failed-precondition", "Sản phẩm không còn khả dụng.");
    }
    sellerId = postOwnerId(post);
    if (!sellerId) fail("failed-precondition", "Bài đăng chưa có chủ sở hữu hợp lệ.");
    if (sellerId === uid) fail("failed-precondition", "Không thể tự mua sản phẩm của mình.");
    const price = post.get("price");
    if (!Number.isSafeInteger(price) || price <= 0) fail("failed-precondition", "Giá sản phẩm không hợp lệ.");
    const address = addressRef ? await tx.get(addressRef) : null;
    if (addressRef && !address.exists) fail("not-found", "Không tìm thấy địa chỉ nhận hàng.");
    const shippingAddress = validatedAddress(address);
    if (request.data.expectedPrice != null && request.data.expectedPrice !== price)
      fail("failed-precondition", "Giá sản phẩm đã thay đổi. Vui lòng tải lại báo giá.");
    if (request.data.expectedShippingFee != null && request.data.expectedShippingFee !== shippingFee)
      fail("failed-precondition", "Phí vận chuyển đã thay đổi. Vui lòng tải lại báo giá.");
    const orderRef = db.collection("orders").doc();
    const paymentRef = db.collection("payments").doc();
    orderId = orderRef.id;
    tx.create(orderRef, {
      orderType: "PAID", buyerId: uid, sellerId, postId, price, shippingFee,
      postSnapshot: orderPostSnapshot(post),
      shippingAddress,
      orderStatus: "PENDING_PAYMENT", paymentStatus: "PENDING", shippingStatus: "READY",
      paymentId: paymentRef.id, trackingCode: "", paymentDeadline: new Date(Date.now() + 15 * 60 * 1000),
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp()
    });
    tx.create(paymentRef, {
      orderId, buyerId: uid, sellerId, amount: price + shippingFee,
      currency: "VND", status: "PENDING", provider: "SIMULATED",
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp()
    });
    writeOrderEvent(tx, orderRef, "", "PENDING_PAYMENT", uid, "Đơn được tạo; thời hạn thanh toán 15 phút.");
    tx.update(postRef, { status: "RESERVED", updatedAt: FieldValue.serverTimestamp(), reservedForOrderId: orderId });
    tx.create(idemRef, { orderId, postId, addressId, createdAt: FieldValue.serverTimestamp() });
  });
  if (sellerId) await notify(sellerId, { type: "ORDER_AWAITING_PAYMENT", title: "Có đơn hàng mới", body: `Đơn ${orderId} đang chờ người mua thanh toán.`, orderId });
  return { orderId };
});

exports.simulatePaymentResult = onCall(async (request) => {
  const uid = uidOf(request);
  if (!simulationEnabled("PAYMENTS")) fail("failed-precondition", "Thanh toán mô phỏng chỉ khả dụng trên Emulator khi được bật.");
  const orderId = cleanDocumentId(request.data.orderId, "Đơn hàng");
  const status = cleanText(request.data.status, 16, "Kết quả");
  if (!["PAID", "FAILED"].includes(status)) fail("invalid-argument", "Kết quả thanh toán không hợp lệ.");
  const orderRef = db.collection("orders").doc(orderId);
  let buyerId; let sellerId; let alreadyApplied = false;
  await db.runTransaction(async (tx) => {
    const order = await tx.get(orderRef);
    const orderPosts = await readOrderPosts(tx, order);
    if (!order.exists || order.get("buyerId") !== uid) fail("permission-denied", "Đơn hàng không thể thanh toán.");
    if (order.get("paymentStatus") === status) { alreadyApplied = true; return; }
    if (order.get("orderStatus") !== "PENDING_PAYMENT") fail("permission-denied", "Đơn hàng không thể thanh toán.");
    if (order.get("paymentDeadline") && order.get("paymentDeadline").toDate() <= new Date()) fail("deadline-exceeded", "Đơn hàng đã hết thời hạn thanh toán.");
    const paymentRef = db.collection("payments").doc(order.get("paymentId"));
    const postRef = db.collection("posts").doc(order.get("postId"));
    const [payment, post] = await Promise.all([tx.get(paymentRef), tx.get(postRef)]);
    if (!payment.exists || !post.exists || post.get("reservedForOrderId") !== orderId
        || orderPosts.some(entry => !entry.snapshot.exists || entry.snapshot.get("reservedForOrderId") !== orderId))
      fail("failed-precondition", "Thông tin đơn hàng không hợp lệ.");
    buyerId = order.get("buyerId"); sellerId = order.get("sellerId");
    const orderStatus = status === "PAID" ? "CONFIRMED" : "PAYMENT_FAILED";
    tx.update(paymentRef, { status, provider: "SIMULATED", providerReference: `SIM-${orderId}`, updatedAt: FieldValue.serverTimestamp() });
    tx.update(orderRef, { paymentStatus: status, orderStatus, ...(status === "PAID" ? { sellerResponseDeadline: new Date(Date.now() + 48 * 60 * 60 * 1000) } : {}), updatedAt: FieldValue.serverTimestamp() });
    writeOrderEvent(tx, orderRef, "PENDING_PAYMENT", orderStatus, "SIMULATED_PAYMENT", "Thanh toán mô phỏng.");
    if (status === "PAID") {
      tx.create(db.collection("moneyTransactions").doc(`received_${orderId}`), { orderId, paymentId: paymentRef.id, type: "PAYMENT_RECEIVED", amount: payment.get("amount"), mode: "SIMULATED", status: "HOLDING", idempotencyKey: `received_${orderId}`, createdAt: FieldValue.serverTimestamp() });
      writeOrderPosts(tx, orderPosts, { status: "SOLD", updatedAt: FieldValue.serverTimestamp() });
    } else {
      writeOrderPosts(tx, orderPosts, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
    }
  });
  if (!alreadyApplied) await Promise.all([buyerId, sellerId].map((targetUid) => notify(targetUid, { type: "PAYMENT_UPDATED", title: "Cập nhật thanh toán", body: `Thanh toán đơn ${orderId}: ${status}`, orderId })));
  return { ok: true };
});

exports.cancelOrder = onCall(async (request) => {
  const uid = uidOf(request);
  const orderId = cleanDocumentId(request.data.orderId, "Đơn hàng");
  const orderRef = db.collection("orders").doc(orderId);
  let sellerId; let postId; let paymentId;
  await db.runTransaction(async (tx) => {
    const order = await tx.get(orderRef);
    const orderPosts = await readOrderPosts(tx, order);
    if (!order.exists || order.get("buyerId") !== uid || !["PENDING_PAYMENT", "CONFIRMED"].includes(order.get("orderStatus"))) {
      fail("failed-precondition", "Đơn hàng không thể hủy.");
    }
    sellerId = order.get("sellerId"); postId = order.get("postId"); paymentId = order.get("paymentId");
    const postRef = db.collection("posts").doc(postId);
    const post = await tx.get(postRef);
    const requestRef = order.get("requestId") ? db.collection("freeRequests").doc(order.get("requestId")) : null;
    const freeRequest = requestRef ? await tx.get(requestRef) : null;
    const paid = order.get("paymentStatus") === "PAID";
    const paymentRef = paymentId ? db.collection("payments").doc(paymentId) : null;
    const payment = paymentRef ? await tx.get(paymentRef) : null;
    const previousStatus = order.get("orderStatus");
    writeOrderEvent(tx, orderRef, previousStatus, "CANCELLED", uid, "Người mua hủy trước khi người bán chuẩn bị hàng.");
    const paymentStatus = order.get("orderType") === "FREE" ? "NOT_REQUIRED" : (paid ? "REFUNDED" : "CANCELLED");
    tx.update(orderRef, { orderStatus: "CANCELLED", paymentStatus, updatedAt: FieldValue.serverTimestamp() });
    if (freeRequest && freeRequest.exists && freeRequest.get("status") === "ACCEPTED") tx.update(requestRef, { status: "CANCELLED", updatedAt: FieldValue.serverTimestamp() });
    if (post.exists && post.get("reservedForOrderId") === orderId) writeOrderPosts(tx, orderPosts, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
    if (paymentRef && payment && payment.exists) tx.update(paymentRef, { status: paid ? "REFUNDED" : "CANCELLED", updatedAt: FieldValue.serverTimestamp() });
    if (paid && payment && payment.exists) {
      const amount = payment.get("amount");
      tx.update(db.collection("moneyTransactions").doc(`received_${orderId}`), { status: "REFUNDED", refundedAt: FieldValue.serverTimestamp() });
      tx.set(db.collection("refunds").doc(orderId), { orderId, paymentId, amount, reason: "Người mua hủy trước khi người bán chuẩn bị hàng", status: "COMPLETED", mode: "SIMULATED", createdAt: FieldValue.serverTimestamp() });
      tx.set(db.collection("moneyTransactions").doc(`refund_${orderId}`), { orderId, paymentId, type: "REFUND", amount, mode: "SIMULATED", idempotencyKey: `refund_${orderId}`, createdAt: FieldValue.serverTimestamp() });
    }
  });
  await notify(sellerId, { type: "ORDER_CANCELLED", title: "Đơn hàng đã bị hủy", body: `Đơn ${orderId} đã được người mua hủy.`, orderId });
  return { ok: true };
});

exports.rejectOrder = onCall(async (request) => {
  const uid = uidOf(request);
  const orderId = cleanDocumentId(request.data.orderId, "Đơn hàng");
  const reason = cleanText(request.data.reason, 300, "Lý do", false);
  const orderRef = db.collection("orders").doc(orderId);
  let buyerId;
  await db.runTransaction(async (tx) => {
    const order = await tx.get(orderRef);
    const orderPosts = await readOrderPosts(tx, order);
    if (!order.exists || order.get("sellerId") !== uid || !["PENDING_PAYMENT", "CONFIRMED"].includes(order.get("orderStatus"))) {
      fail("failed-precondition", "Đơn hàng không thể bị từ chối.");
    }
    buyerId = order.get("buyerId");
    const postRef = db.collection("posts").doc(order.get("postId"));
    const post = await tx.get(postRef);
    const paymentRef = order.get("paymentId") ? db.collection("payments").doc(order.get("paymentId")) : null;
    const payment = paymentRef ? await tx.get(paymentRef) : null;
    const freeRequestRef = order.get("requestId") ? db.collection("freeRequests").doc(order.get("requestId")) : null;
    const freeRequest = freeRequestRef ? await tx.get(freeRequestRef) : null;
    const paid = order.get("paymentStatus") === "PAID";
    const nextPaymentStatus = order.get("orderType") === "FREE" ? "NOT_REQUIRED" : (paid ? "REFUNDED" : "CANCELLED");
    writeOrderEvent(tx, orderRef, order.get("orderStatus"), "CANCELLED", uid, reason || "Người bán từ chối đơn hàng.");
    tx.update(orderRef, { orderStatus: "CANCELLED", paymentStatus: nextPaymentStatus, cancellationReason: reason || "SELLER_REJECTED", updatedAt: FieldValue.serverTimestamp() });
    if (freeRequestRef && freeRequest && freeRequest.exists && freeRequest.get("status") === "ACCEPTED") {
      tx.update(freeRequestRef, { status: "CANCELLED", updatedAt: FieldValue.serverTimestamp() });
    }
    if (post.exists && post.get("reservedForOrderId") === orderId) {
      writeOrderPosts(tx, orderPosts, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
    }
    if (paymentRef && payment && payment.exists) {
      tx.update(paymentRef, { status: paid ? "REFUNDED" : "CANCELLED", updatedAt: FieldValue.serverTimestamp() });
    }
    if (paid && payment && payment.exists) {
      const amount = payment.get("amount");
      tx.set(db.collection("moneyTransactions").doc(`received_${orderId}`), { status: "REFUNDED", refundedAt: FieldValue.serverTimestamp() }, { merge: true });
      tx.set(db.collection("refunds").doc(orderId), { orderId, paymentId: paymentRef.id, amount, reason: reason || "Người bán từ chối đơn hàng", status: "COMPLETED", mode: "SIMULATED", createdAt: FieldValue.serverTimestamp() });
      tx.set(db.collection("moneyTransactions").doc(`refund_${orderId}`), { orderId, paymentId: paymentRef.id, type: "REFUND", amount, mode: "SIMULATED", idempotencyKey: `refund_${orderId}`, createdAt: FieldValue.serverTimestamp() });
    }
  });
  await notify(buyerId, { type: "ORDER_REJECTED", title: "Người bán đã từ chối đơn", body: reason || `Đơn ${orderId} đã được hủy và hoàn tiền mô phỏng nếu đã thanh toán.`, orderId });
  return { ok: true };
});

exports.createShipment = onCall(async (request) => {
  const uid = uidOf(request);
  if (!simulationEnabled("SHIPPING")) fail("failed-precondition", "Chưa kết nối hãng vận chuyển. Vận đơn mô phỏng chỉ bật trên Emulator.");
  const orderId = cleanDocumentId(request.data.orderId, "Đơn hàng");
  const requestedCarrier = cleanText(request.data.carrier, 80, "Đơn vị vận chuyển", false);
  if (requestedCarrier && requestedCarrier !== "MOCK_CARRIER") fail("failed-precondition", "Hiện chỉ hỗ trợ đơn vị vận chuyển mô phỏng.");
  const carrier = "MOCK_CARRIER";
  const trackingCode = cleanText(request.data.trackingCode, 100, "Mã vận đơn", false) || `EMU-${orderId}`;
  const orderRef = db.collection("orders").doc(orderId);
  const shipmentRef = db.collection("shipments").doc(orderId);
  let buyerId;
  await db.runTransaction(async (tx) => {
    const [order, priorShipment] = await Promise.all([tx.get(orderRef), tx.get(shipmentRef)]);
    if (order.exists && order.get("sellerId") === uid && priorShipment.exists) return;
    if (!order.exists || order.get("sellerId") !== uid || order.get("orderStatus") !== "READY_TO_SHIP") {
      fail("permission-denied", "Bạn không thể tạo vận đơn cho đơn hàng này.");
    }
    buyerId = order.get("buyerId");
    tx.set(shipmentRef, { orderId, buyerId, sellerId: uid, carrier: "MOCK_CARRIER", mode: "SIMULATED", trackingCode, status: "IN_TRANSIT", events: [{ status: "IN_TRANSIT", description: "Bàn giao cho đơn vị vận chuyển mô phỏng", createdAt: new Date().toISOString() }], updatedAt: FieldValue.serverTimestamp() });
    tx.update(orderRef, { orderStatus: "SHIPPING", shippingStatus: "IN_TRANSIT", trackingCode, updatedAt: FieldValue.serverTimestamp() });
    writeOrderEvent(tx, orderRef, "READY_TO_SHIP", "SHIPPING", uid, "Đã bàn giao cho đơn vị vận chuyển.");
  });
  if (buyerId) await notify(buyerId, { type: "SHIPMENT_UPDATED", title: "Đơn hàng đang được giao", body: `Mã vận đơn ${trackingCode} · ${carrier}`, orderId });
  return { shipmentId: shipmentRef.id };
});

exports.simulateShipmentUpdate = onCall(async (request) => {
  const uid = uidOf(request);
  if (!simulationEnabled("SHIPPING")) fail("failed-precondition", "Cập nhật vận chuyển mô phỏng chỉ khả dụng trên Emulator khi được bật.");
  const orderId = cleanDocumentId(request.data.orderId, "Đơn hàng");
  const status = cleanText(request.data.status, 16, "Trạng thái");
  const description = cleanText(request.data.description, 250, "Mô tả", false);
  if (!["IN_TRANSIT", "DELIVERED", "RETURNED"].includes(status)) fail("invalid-argument", "Trạng thái vận chuyển không hợp lệ.");
  const location = shipmentLocation(request.data.location);
  const orderRef = db.collection("orders").doc(orderId);
  const shipmentRef = db.collection("shipments").doc(orderId);
  let buyerId; let sellerId;
  await db.runTransaction(async (tx) => {
    const order = await tx.get(orderRef);
    if (order.get("shippingProvider") === "GHN") fail("permission-denied", "Đơn GHN chỉ được cập nhật bằng tra cứu GHN đã xác minh.");
    const orderPosts = await readOrderPosts(tx, order);
    if (!order.exists || order.get("sellerId") !== uid || order.get("shippingStatus") !== "IN_TRANSIT") fail("permission-denied", "Chỉ người bán mới có thể giả lập cập nhật vận chuyển.");
    const postRef = db.collection("posts").doc(order.get("postId"));
    const paymentRef = order.get("paymentId") ? db.collection("payments").doc(order.get("paymentId")) : null;
    const post = await tx.get(postRef);
    const payment = paymentRef ? await tx.get(paymentRef) : null;
    const shipment = await tx.get(shipmentRef);
    if (!shipment.exists) fail("not-found", "Không tìm thấy vận đơn.");
    buyerId = order.get("buyerId"); sellerId = order.get("sellerId");
    const nextOrderStatus = status === "IN_TRANSIT" ? "SHIPPING" : status === "DELIVERED" ? "DELIVERED" : (order.get("orderType") === "PAID" ? "REFUNDED" : "CANCELLED");
    tx.update(orderRef, { shippingStatus: status, orderStatus: nextOrderStatus,
      ...(status === "DELIVERED" ? { deliveredAt: FieldValue.serverTimestamp(), inspectionDeadline: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000) } : {}),
      ...(status === "RETURNED" && order.get("orderType") === "PAID" ? { paymentStatus: "REFUNDED" } : {}), updatedAt: FieldValue.serverTimestamp() });
    tx.update(shipmentRef, { status, ...(location ? { location: { ...location, source: "SIMULATED" } } : {}), updatedAt: FieldValue.serverTimestamp(), events: FieldValue.arrayUnion({ status, description, ...(location ? { location } : {}), createdAt: new Date().toISOString() }) });
    writeOrderEvent(tx, orderRef, order.get("orderStatus"), nextOrderStatus, "SIMULATED_CARRIER", description || status);
    if (status === "RETURNED") {
      if (post.exists) writeOrderPosts(tx, orderPosts, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
      if (paymentRef && payment && payment.exists) {
        tx.update(paymentRef, { status: "REFUNDED", updatedAt: FieldValue.serverTimestamp() });
        const amount = order.get("price") + order.get("shippingFee");
        tx.update(db.collection("moneyTransactions").doc(`received_${orderId}`), { status: "REFUNDED", refundedAt: FieldValue.serverTimestamp() });
        tx.set(db.collection("refunds").doc(orderId), { orderId, paymentId: paymentRef.id, amount, reason: "Đơn hàng bị hoàn về người bán", status: "COMPLETED", mode: "SIMULATED", createdAt: FieldValue.serverTimestamp() });
        tx.set(db.collection("moneyTransactions").doc(`refund_${orderId}`), { orderId, paymentId: paymentRef.id, type: "REFUND", amount, mode: "SIMULATED", idempotencyKey: `refund_${orderId}`, createdAt: FieldValue.serverTimestamp() });
      }
    }
  });
  await Promise.all([buyerId, sellerId].map((targetUid) => notify(targetUid, { type: "SHIPMENT_UPDATED", title: "Cập nhật vận chuyển", body: description || status, orderId })));
  return { ok: true };
});

exports.updateOrderStatus = onCall(async (request) => {
  const uid = uidOf(request);
  const orderId = cleanDocumentId(request.data.orderId, "Đơn hàng");
  const nextStatus = cleanText(request.data.status, 32, "Trạng thái");
  if (nextStatus === "COMPLETED") return { ok: true, settled: await settleDelivery(orderId, uid) };
  const orderRef = db.collection("orders").doc(orderId);
  let buyerId; let sellerId; let postId;
  await db.runTransaction(async (tx) => {
    const order = await tx.get(orderRef);
    if (!order.exists) fail("not-found", "Không tìm thấy đơn hàng.");
    buyerId = order.get("buyerId"); sellerId = order.get("sellerId"); postId = order.get("postId");
    const current = order.get("orderStatus");
    if (nextStatus === "COMPLETED" && ["OPEN", "WAITING_EVIDENCE"].includes(order.get("disputeStatus")))
      fail("failed-precondition", "Đơn có tranh chấp đang mở, chưa thể hoàn tất hoặc giải ngân.");
    const sellerSteps = { CONFIRMED: "PREPARING", PREPARING: "READY_TO_SHIP" };
    const allowed = uid === sellerId && sellerSteps[current] === nextStatus;
    if (!allowed) fail("permission-denied", "Chuyển trạng thái đơn hàng không hợp lệ.");
    writeOrderEvent(tx, orderRef, current, nextStatus, uid);
    tx.update(orderRef, { orderStatus: nextStatus, updatedAt: FieldValue.serverTimestamp() });
  });
  const recipient = uid === buyerId ? sellerId : buyerId;
  await notify(recipient, { type: "ORDER_UPDATED", title: "Đơn hàng được cập nhật", body: `Đơn ${orderId}: ${nextStatus}`, orderId });
  return { ok: true };
});

exports.updateShipmentFromCarrier = onRequest({ secrets: [shipmentWebhookToken] }, async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("Method not allowed");
  if (!validShipmentWebhook(req)) return res.status(401).send("Unauthorized");
  const { eventId, orderId, trackingCode, status, description } = req.body || {};
  if (typeof eventId !== "string" || !eventId.trim() || eventId.length > 128
      || typeof orderId !== "string" || !orderId.trim() || orderId.length > 128
      || typeof trackingCode !== "string" || !trackingCode || trackingCode.length > 100
      || !["IN_TRANSIT", "DELIVERED", "RETURNED"].includes(status)) return res.status(400).send("Invalid event");
  const normalizedOrderId = String(orderId);
  if (normalizedOrderId.includes("/")) return res.status(400).send("Invalid orderId");
  const orderRef = db.collection("orders").doc(normalizedOrderId);
  const shipmentRef = db.collection("shipments").doc(normalizedOrderId);
  const eventRef = db.doc(`webhookEvents/shipment_${crypto.createHash("sha256").update(eventId.trim()).digest("hex")}`);
  let buyerId; let duplicateUpdate = false;
  try {
    await db.runTransaction(async (tx) => {
      duplicateUpdate = false;
      const [order, shipment, event] = await Promise.all([tx.get(orderRef), tx.get(shipmentRef), tx.get(eventRef)]);
      if (order.get("shippingProvider") === "GHN") fail("permission-denied", "Đơn GHN chỉ được cập nhật bằng tra cứu GHN đã xác minh.");
      const orderPosts = await readOrderPosts(tx, order);
      if (event.exists) {
        if (event.get("orderId") !== normalizedOrderId || event.get("status") !== status || event.get("trackingCode") !== trackingCode)
          throw new Error("Event identifier already used");
        duplicateUpdate = true; return;
      }
      if (!order.exists || !shipment.exists || shipment.get("trackingCode") !== trackingCode) throw new Error("Shipment not found");
      buyerId = order.get("buyerId");
      const currentStatus = order.get("shippingStatus");
      if (currentStatus === status) {
        tx.create(eventRef, { orderId: normalizedOrderId, trackingCode, status, duplicate: true, createdAt: FieldValue.serverTimestamp() });
        duplicateUpdate = true; return;
      }
      if (currentStatus !== "IN_TRANSIT" || order.get("orderStatus") !== "SHIPPING") throw new Error("Shipment is already finalized");
      if (status === "DELIVERED" && currentStatus !== "IN_TRANSIT") throw new Error("Invalid shipment transition");
      if (status === "RETURNED" && currentStatus !== "IN_TRANSIT") throw new Error("Invalid shipment transition");
      const postRef = status === "RETURNED" ? db.collection("posts").doc(order.get("postId")) : null;
      const post = postRef ? await tx.get(postRef) : null;
      const paymentRef = status === "RETURNED" && order.get("paymentId") ? db.collection("payments").doc(order.get("paymentId")) : null;
      const payment = paymentRef ? await tx.get(paymentRef) : null;
      const orderStatus = status === "DELIVERED" ? "DELIVERED" : (status === "RETURNED" ? (order.get("orderType") === "PAID" ? "REFUNDED" : "CANCELLED") : "SHIPPING");
      const orderPatch = { shippingStatus: status, orderStatus, updatedAt: FieldValue.serverTimestamp() };
      if (status === "DELIVERED") {
        orderPatch.deliveredAt = FieldValue.serverTimestamp();
        orderPatch.inspectionDeadline = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000);
      }
      if (status === "RETURNED") orderPatch.paymentStatus = order.get("orderType") === "PAID" ? "REFUNDED" : "NOT_REQUIRED";
      tx.update(orderRef, orderPatch);
      tx.update(shipmentRef, { status, updatedAt: FieldValue.serverTimestamp(), events: FieldValue.arrayUnion({ status, description: String(description || ""), createdAt: new Date().toISOString() }) });
      writeOrderEvent(tx, orderRef, order.get("orderStatus"), orderPatch.orderStatus, "MOCK_CARRIER", String(description || status));
      tx.create(eventRef, { orderId: normalizedOrderId, trackingCode, status, createdAt: FieldValue.serverTimestamp() });
      if (status === "RETURNED") {
        if (post.exists) writeOrderPosts(tx, orderPosts, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
        if (paymentRef && payment.exists) {
          tx.update(paymentRef, { status: "REFUNDED", updatedAt: FieldValue.serverTimestamp() });
          const refundAmount = order.get("price") + order.get("shippingFee");
          tx.update(db.collection("moneyTransactions").doc(`received_${orderId}`), { status: "REFUNDED", refundedAt: FieldValue.serverTimestamp() });
          tx.set(db.collection("refunds").doc(String(orderId)), { orderId: String(orderId), paymentId: order.get("paymentId"), amount: refundAmount, reason: "Đơn hàng bị hoàn về người bán", status: "COMPLETED", mode: "SIMULATED", createdAt: FieldValue.serverTimestamp() });
          tx.set(db.collection("moneyTransactions").doc(`refund_${orderId}`), { orderId: String(orderId), paymentId: order.get("paymentId"), type: "REFUND", amount: refundAmount, mode: "SIMULATED", idempotencyKey: `refund_${orderId}`, createdAt: FieldValue.serverTimestamp() });
        }
      }
    });
    if (duplicateUpdate) return res.status(200).json({ ok: true, duplicate: true });
    await notify(buyerId, { type: "SHIPMENT_UPDATED", title: "Cập nhật vận chuyển", body: String(description || status), orderId: String(orderId) });
    return res.status(200).json({ ok: true });
  } catch (error) {
    return res.status(404).send(error.message || "Shipment not found");
  }
});

exports.updatePaymentFromProvider = onRequest({ secrets: [paymentWebhookToken] }, async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("Method not allowed");
  if (!validPaymentWebhook(req)) return res.status(401).send("Unauthorized");
  const { eventId, orderId, providerReference, status } = req.body || {};
  if (typeof eventId !== "string" || !eventId.trim() || eventId.length > 128
      || typeof orderId !== "string" || !orderId.trim() || orderId.length > 128
      || typeof providerReference !== "string" || !providerReference.trim() || providerReference.length > 200
      || !["PAID", "FAILED", "REFUNDED"].includes(status)) return res.status(400).send("Invalid event");
  const normalizedOrderId = orderId.trim();
  const normalizedEventId = eventId.trim();
  if (normalizedOrderId.includes("/") || normalizedEventId.includes("/")) return res.status(400).send("Invalid identifiers");
  const orderRef = db.collection("orders").doc(normalizedOrderId);
  const eventRef = db.collection("webhookEvents").doc(`payment_${crypto.createHash("sha256").update(normalizedEventId).digest("hex")}`);
  let buyerId; let sellerId; let postId; let duplicateEvent = false;
  try {
    await db.runTransaction(async (tx) => {
      const event = await tx.get(eventRef);
      if (event.exists) { duplicateEvent = true; return; }
      const order = await tx.get(orderRef);
    const orderPosts = await readOrderPosts(tx, order);
      if (!order.exists || !order.get("paymentId")) throw new Error("Order not found");
      buyerId = order.get("buyerId"); sellerId = order.get("sellerId"); postId = order.get("postId");
      const paymentRef = db.collection("payments").doc(order.get("paymentId"));
      const payment = await tx.get(paymentRef);
      if (!payment.exists || payment.get("orderId") !== String(orderId)) throw new Error("Payment not found");
      const postRef = db.collection("posts").doc(postId);
      const post = await tx.get(postRef);
      const priorPaymentStatus = order.get("paymentStatus");
      if (priorPaymentStatus === status) {
        duplicateEvent = true;
        tx.create(eventRef, { provider: payment.get("provider"), eventId: normalizedEventId, status: "IGNORED_ALREADY_APPLIED", createdAt: FieldValue.serverTimestamp() });
        return;
      }
      if (status !== "REFUNDED" && order.get("orderStatus") !== "PENDING_PAYMENT") throw new Error("Order is no longer awaiting payment");
      const isValidTransition = (priorPaymentStatus === "PENDING" && ["PAID", "FAILED"].includes(status))
        || (priorPaymentStatus === "PAID" && status === "REFUNDED");
      if (!isValidTransition) throw new Error("Payment is already finalized");
      if (status === "PAID" && order.get("paymentDeadline")
          && order.get("paymentDeadline").toDate() <= new Date()) throw new Error("Payment deadline has expired");
      if (status === "REFUNDED" && !["CONFIRMED", "PREPARING", "READY_TO_SHIP"].includes(order.get("orderStatus"))) {
        throw new Error("Refund must be handled by the shipment or dispute flow after shipping starts");
      }
      tx.update(paymentRef, { status, providerReference: String(providerReference), updatedAt: FieldValue.serverTimestamp() });
      const nextOrder = status === "PAID" ? "CONFIRMED" : (status === "REFUNDED" ? "REFUNDED" : "PAYMENT_FAILED");
      tx.update(orderRef, { paymentStatus: status, orderStatus: nextOrder,
        ...(status === "PAID" ? { sellerResponseDeadline: new Date(Date.now() + 48 * 60 * 60 * 1000) } : {}),
        updatedAt: FieldValue.serverTimestamp() });
      writeOrderEvent(tx, orderRef, order.get("orderStatus"), nextOrder, "PAYMENT_PROVIDER", `Payment ${status}`);
      if (status === "PAID") {
        tx.create(db.collection("moneyTransactions").doc(`received_${orderId}`), { orderId, paymentId: paymentRef.id, type: "PAYMENT_RECEIVED", amount: payment.get("amount"), mode: "SIMULATED", status: "HOLDING", idempotencyKey: `received_${orderId}`, createdAt: FieldValue.serverTimestamp() });
      } else if (status === "REFUNDED") {
        tx.update(db.collection("moneyTransactions").doc(`received_${orderId}`), { status: "REFUNDED", refundedAt: FieldValue.serverTimestamp() });
        tx.create(db.collection("refunds").doc(orderId), { orderId, paymentId: paymentRef.id, amount: payment.get("amount"), reason: "Provider refund", status: "COMPLETED", mode: "SIMULATED", createdAt: FieldValue.serverTimestamp() });
        tx.create(db.collection("moneyTransactions").doc(`refund_${orderId}`), { orderId, paymentId: paymentRef.id, type: "REFUND", amount: payment.get("amount"), mode: "SIMULATED", idempotencyKey: `refund_${orderId}`, createdAt: FieldValue.serverTimestamp() });
      }
      if (post.exists && post.get("reservedForOrderId") === String(orderId)) {
        if (status === "PAID") writeOrderPosts(tx, orderPosts, { status: "SOLD", updatedAt: FieldValue.serverTimestamp() });
        else if (status === "FAILED" || status === "REFUNDED") writeOrderPosts(tx, orderPosts, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
      }
      tx.create(eventRef, { provider: payment.get("provider"), eventId: normalizedEventId, payloadHash: crypto.createHash("sha256").update(JSON.stringify(req.body)).digest("hex"), status: "PROCESSED", createdAt: FieldValue.serverTimestamp() });
    });
    if (duplicateEvent) return res.status(200).json({ ok: true, duplicate: true });
    await Promise.all([buyerId, sellerId].map((uid) => notify(uid, { type: "PAYMENT_UPDATED", title: "Cập nhật thanh toán", body: `Thanh toán đơn ${orderId}: ${status}`, orderId: String(orderId) })));
    return res.status(200).json({ ok: true });
  } catch (error) {
    return res.status(404).send(error.message || "Payment update failed");
  }
});

exports.expireUnpaidAndUnconfirmedOrders = onSchedule("every 15 minutes", async () => {
  const now = new Date();
  const delivered = await db.collection("orders").where("orderStatus", "==", "DELIVERED")
    .where("inspectionDeadline", "<=", now).limit(200).get();
  for (const order of delivered.docs) await settleDelivery(order.id);
  const unpaid = await db.collection("orders").where("orderStatus", "==", "PENDING_PAYMENT")
    .where("paymentDeadline", "<=", now).limit(200).get();
  for (const orderDoc of unpaid.docs) {
    await db.runTransaction(async (tx) => {
      const order = await tx.get(orderDoc.ref);
      const orderPosts = await readOrderPosts(tx, order);
      if (!order.exists || order.get("orderStatus") !== "PENDING_PAYMENT") return;
      const postRef = db.collection("posts").doc(order.get("postId"));
      const post = await tx.get(postRef);
      tx.update(orderDoc.ref, { orderStatus: "CANCELLED", paymentStatus: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
      writeOrderEvent(tx, orderDoc.ref, "PENDING_PAYMENT", "CANCELLED", "SYSTEM", "Quá hạn thanh toán 15 phút.");
      if (post.exists && post.get("reservedForOrderId") === orderDoc.id) writeOrderPosts(tx, orderPosts, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
      if (order.get("paymentId")) tx.update(db.collection("payments").doc(order.get("paymentId")), { status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
    });
    await notify(orderDoc.get("buyerId"), { type: "ORDER_EXPIRED", title: "Đơn hàng đã hết hạn", body: `Đơn ${orderDoc.id} đã được mở lại vì chưa thanh toán.`, orderId: orderDoc.id });
  }
  const unpaidSellers = await db.collection("orders").where("orderStatus", "==", "CONFIRMED")
    .where("sellerResponseDeadline", "<=", now).limit(200).get();
  for (const orderDoc of unpaidSellers.docs) {
    await db.runTransaction(async (tx) => {
      const order = await tx.get(orderDoc.ref);
      const orderPosts = await readOrderPosts(tx, order);
      if (!order.exists || order.get("orderStatus") !== "CONFIRMED") return;
      const postRef = db.collection("posts").doc(order.get("postId"));
      const post = await tx.get(postRef);
      tx.update(orderDoc.ref, { orderStatus: "REFUNDED", paymentStatus: "REFUNDED", updatedAt: FieldValue.serverTimestamp() });
      writeOrderEvent(tx, orderDoc.ref, "CONFIRMED", "REFUNDED", "SYSTEM", "Người bán không phản hồi trong 48 giờ.");
      tx.set(db.collection("refunds").doc(orderDoc.id), { orderId: orderDoc.id, paymentId: order.get("paymentId"), amount: order.get("price") + order.get("shippingFee"), reason: "Người bán không phản hồi trong 48 giờ", status: "COMPLETED", mode: "SIMULATED", createdAt: FieldValue.serverTimestamp() });
      if (order.get("paymentId")) tx.update(db.collection("payments").doc(order.get("paymentId")), { status: "REFUNDED", updatedAt: FieldValue.serverTimestamp() });
      tx.set(db.collection("moneyTransactions").doc(`refund_${orderDoc.id}`), { orderId: orderDoc.id, paymentId: order.get("paymentId"), type: "REFUND", amount: order.get("price") + order.get("shippingFee"), mode: "SIMULATED", idempotencyKey: `refund_${orderDoc.id}`, createdAt: FieldValue.serverTimestamp() });
      tx.update(db.collection("moneyTransactions").doc(`received_${orderDoc.id}`), { status: "REFUNDED", refundedAt: FieldValue.serverTimestamp() });
      if (post.exists && post.get("reservedForOrderId") === orderDoc.id) writeOrderPosts(tx, orderPosts, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
    });
    await Promise.all([orderDoc.get("buyerId"), orderDoc.get("sellerId")].map((uid) => notify(uid, { type: "ORDER_TIMEOUT", title: "Đơn hàng đã hết hạn phản hồi", body: `Đơn ${orderDoc.id} đã được hoàn tiền mô phỏng.`, orderId: orderDoc.id })));
  }
});

exports.markNotificationRead = onCall(async (request) => {
  const uid = uidOf(request);
  const notificationId = cleanDocumentId(request.data.notificationId, "Thông báo");
  await db.collection("users").doc(uid).collection("notifications").doc(notificationId).update({ read: true, readAt: FieldValue.serverTimestamp() });
  return { ok: true };
});

Object.assign(exports, require("./cart-checkout")({ db, onCall, uidOf, cleanDocumentId, cleanText, fail, FieldValue,
  shippingFeeVnd, validatedAddress, postOwnerId, postType, orderPostSnapshot, writeOrderEvent, requireVerifiedAccount }));
Object.assign(exports, require("./admin")({ onCall, db, getAuth, getStorage, FieldValue, fail, cleanText, cleanDocumentId, writeOrderEvent }));
Object.assign(exports, require("./account")({ onCall, db, getStorage, FieldValue, uidOf, fail, cleanText, cleanDocumentId }));

require('./ghn-integration')({ exports, db, getAuth, onCall, onRequest, defineSecret, fail, uidOf, cleanDocumentId, cleanText, FieldValue, logger, requireVerifiedAccount, postOwnerId, postType, orderPostSnapshot, writeOrderEvent });
