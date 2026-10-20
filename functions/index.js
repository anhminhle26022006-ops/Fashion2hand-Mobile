const crypto = require("node:crypto");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");

initializeApp();
const db = getFirestore();
const paymentWebhookToken = defineSecret("PAYMENT_WEBHOOK_TOKEN");
const shipmentWebhookToken = defineSecret("SHIPMENT_WEBHOOK_TOKEN");

const fail = (code, message) => { throw new HttpsError(code, message); };
function uidOf(request) {
  if (!request.auth) fail("unauthenticated", "Vui lòng đăng nhập.");
  return request.auth.uid;
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
  return tariff.exists && Number.isSafeInteger(tariff.get("minimumFeeVnd"))
    ? Math.max(0, tariff.get("minimumFeeVnd")) : 15000;
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
    if (!post.exists || postOwnerId(post) !== otherUserId) fail("failed-precondition", "Bài đăng không thuộc người nhận.");
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
  const messageRef = chatRef.collection("messages").doc();
  let recipientId;
  await db.runTransaction(async (tx) => {
    const chat = await tx.get(chatRef);
    const participants = chat.exists ? chat.get("participantIds") : null;
    if (!participants || !participants.includes(uid) || participants.length !== 2) {
      fail("permission-denied", "Bạn không thuộc cuộc trò chuyện này.");
    }
    recipientId = participants.find((id) => id !== uid);
    tx.create(messageRef, { senderId: uid, text, createdAt: FieldValue.serverTimestamp(), read: false });
    tx.update(chatRef, {
      lastMessage: text, lastMessageAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
      [`unreadCounts.${recipientId}`]: FieldValue.increment(1)
    });
  });
  await notify(recipientId, { type: "MESSAGE", title: "Tin nhắn mới", body: text.slice(0, 120), chatId });
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
        tx.update(chatRef, { [`unreadCounts.${uid}`]: 0, updatedAt: FieldValue.serverTimestamp() });
        return;
      }
      const unreadCounts = currentChat.get("unreadCounts") || {};
      count = unread.size;
      unread.docs.forEach((message) => tx.update(message.ref, { read: true, readAt: FieldValue.serverTimestamp() }));
      tx.update(chatRef, { [`unreadCounts.${uid}`]: Math.max(0, Number(unreadCounts[uid] || 0) - count), updatedAt: FieldValue.serverTimestamp() });
    });
    marked += count;
    if (count < 450) break;
  }
  return { marked };
});

exports.createFreeRequest = onCall(async (request) => {
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
    if (prior.exists && !["REJECTED", "CANCELLED"].includes(prior.get("status"))) fail("already-exists", "Bạn đã gửi yêu cầu cho món đồ này.");
    const address = addressRef ? await tx.get(addressRef) : null;
    if (addressRef && !address.exists) fail("not-found", "Không tìm thấy địa chỉ nhận hàng.");
    const shippingAddress = address && address.exists ? addressSnapshot(address) : null;
    if (prior.exists) {
      tx.update(requestRef, { note, addressId: addressId || null, shippingAddress, status: "PENDING", createdAt: now, updatedAt: FieldValue.serverTimestamp() });
      return;
    }
    tx.create(requestRef, {
      postId, ownerId, requesterId: uid, note, addressId: addressId || null, shippingAddress, status: "PENDING",
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
    const orderRef = db.collection("orders").doc();
    const paymentRef = db.collection("payments").doc();
    orderId = orderRef.id;
    tx.create(orderRef, {
      orderType: "PAID", buyerId: uid, sellerId, postId, price, shippingFee,
      postSnapshot: orderPostSnapshot(post),
      shippingAddress: address && address.exists ? addressSnapshot(address) : null,
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
    tx.create(idemRef, { orderId, postId, createdAt: FieldValue.serverTimestamp() });
  });
  if (sellerId) await notify(sellerId, { type: "ORDER_AWAITING_PAYMENT", title: "Có đơn hàng mới", body: `Đơn ${orderId} đang chờ người mua thanh toán.`, orderId });
  return { orderId };
});

exports.simulatePaymentResult = onCall(async (request) => {
  const uid = uidOf(request);
  if (process.env.ENABLE_SIMULATED_PAYMENTS !== "true") fail("failed-precondition", "Thanh toán mô phỏng đang tắt.");
  const orderId = cleanDocumentId(request.data.orderId, "Đơn hàng");
  const status = cleanText(request.data.status, 16, "Kết quả");
  if (!["PAID", "FAILED"].includes(status)) fail("invalid-argument", "Kết quả thanh toán không hợp lệ.");
  const orderRef = db.collection("orders").doc(orderId);
  let buyerId; let sellerId;
  await db.runTransaction(async (tx) => {
    const order = await tx.get(orderRef);
    if (!order.exists || order.get("buyerId") !== uid || order.get("orderStatus") !== "PENDING_PAYMENT") fail("permission-denied", "Đơn hàng không thể thanh toán.");
    if (order.get("paymentDeadline") && order.get("paymentDeadline").toDate() <= new Date()) fail("deadline-exceeded", "Đơn hàng đã hết thời hạn thanh toán.");
    const paymentRef = db.collection("payments").doc(order.get("paymentId"));
    const postRef = db.collection("posts").doc(order.get("postId"));
    const [payment, post] = await Promise.all([tx.get(paymentRef), tx.get(postRef)]);
    if (!payment.exists || !post.exists || post.get("reservedForOrderId") !== orderId) fail("failed-precondition", "Thông tin đơn hàng không hợp lệ.");
    buyerId = order.get("buyerId"); sellerId = order.get("sellerId");
    const orderStatus = status === "PAID" ? "CONFIRMED" : "PAYMENT_FAILED";
    tx.update(paymentRef, { status, provider: "SIMULATED", providerReference: `SIM-${orderId}`, updatedAt: FieldValue.serverTimestamp() });
    tx.update(orderRef, { paymentStatus: status, orderStatus, ...(status === "PAID" ? { sellerResponseDeadline: new Date(Date.now() + 48 * 60 * 60 * 1000) } : {}), updatedAt: FieldValue.serverTimestamp() });
    writeOrderEvent(tx, orderRef, "PENDING_PAYMENT", orderStatus, "SIMULATED_PAYMENT", "Thanh toán mô phỏng.");
    if (status === "PAID") {
      tx.create(db.collection("moneyTransactions").doc(`received_${orderId}`), { orderId, paymentId: paymentRef.id, type: "PAYMENT_RECEIVED", amount: payment.get("amount"), mode: "SIMULATED", status: "HOLDING", idempotencyKey: `received_${orderId}`, createdAt: FieldValue.serverTimestamp() });
      tx.update(postRef, { status: "SOLD", updatedAt: FieldValue.serverTimestamp() });
    } else {
      tx.update(postRef, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
    }
  });
  await Promise.all([buyerId, sellerId].map((targetUid) => notify(targetUid, { type: "PAYMENT_UPDATED", title: "Cập nhật thanh toán", body: `Thanh toán đơn ${orderId}: ${status}`, orderId })));
  return { ok: true };
});

exports.cancelOrder = onCall(async (request) => {
  const uid = uidOf(request);
  const orderId = cleanDocumentId(request.data.orderId, "Đơn hàng");
  const orderRef = db.collection("orders").doc(orderId);
  let sellerId; let postId; let paymentId;
  await db.runTransaction(async (tx) => {
    const order = await tx.get(orderRef);
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
    if (post.exists && post.get("reservedForOrderId") === orderId) tx.update(postRef, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
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
      tx.update(postRef, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
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
  const orderId = cleanDocumentId(request.data.orderId, "Đơn hàng");
  const requestedCarrier = cleanText(request.data.carrier, 80, "Đơn vị vận chuyển", false);
  if (requestedCarrier && requestedCarrier !== "MOCK_CARRIER") fail("failed-precondition", "Hiện chỉ hỗ trợ đơn vị vận chuyển mô phỏng.");
  const carrier = "MOCK_CARRIER";
  const trackingCode = cleanText(request.data.trackingCode, 100, "Mã vận đơn");
  const orderRef = db.collection("orders").doc(orderId);
  const shipmentRef = db.collection("shipments").doc(orderId);
  let buyerId;
  await db.runTransaction(async (tx) => {
    const order = await tx.get(orderRef);
    if (!order.exists || order.get("sellerId") !== uid || order.get("orderStatus") !== "READY_TO_SHIP") {
      fail("permission-denied", "Bạn không thể tạo vận đơn cho đơn hàng này.");
    }
    buyerId = order.get("buyerId");
    tx.set(shipmentRef, { orderId, buyerId, sellerId: uid, carrier: "MOCK_CARRIER", trackingCode, status: "IN_TRANSIT", events: [{ status: "IN_TRANSIT", createdAt: new Date().toISOString() }], updatedAt: FieldValue.serverTimestamp() });
    tx.update(orderRef, { orderStatus: "SHIPPING", shippingStatus: "IN_TRANSIT", trackingCode, updatedAt: FieldValue.serverTimestamp() });
    writeOrderEvent(tx, orderRef, "READY_TO_SHIP", "SHIPPING", uid, "Đã bàn giao cho đơn vị vận chuyển.");
  });
  await notify(buyerId, { type: "SHIPMENT_UPDATED", title: "Đơn hàng đang được giao", body: `Mã vận đơn ${trackingCode} · ${carrier}`, orderId });
  return { shipmentId: shipmentRef.id };
});

exports.simulateShipmentUpdate = onCall(async (request) => {
  const uid = uidOf(request);
  if (process.env.ENABLE_SIMULATED_SHIPPING !== "true") fail("failed-precondition", "Cập nhật vận chuyển mô phỏng đang tắt.");
  const orderId = cleanDocumentId(request.data.orderId, "Đơn hàng");
  const status = cleanText(request.data.status, 16, "Trạng thái");
  const description = cleanText(request.data.description, 250, "Mô tả", false);
  if (!["DELIVERED", "RETURNED"].includes(status)) fail("invalid-argument", "Trạng thái vận chuyển không hợp lệ.");
  const orderRef = db.collection("orders").doc(orderId);
  const shipmentRef = db.collection("shipments").doc(orderId);
  let buyerId; let sellerId;
  await db.runTransaction(async (tx) => {
    const order = await tx.get(orderRef);
    if (!order.exists || order.get("sellerId") !== uid || order.get("shippingStatus") !== "IN_TRANSIT") fail("permission-denied", "Chỉ người bán mới có thể giả lập cập nhật vận chuyển.");
    const postRef = db.collection("posts").doc(order.get("postId"));
    const paymentRef = order.get("paymentId") ? db.collection("payments").doc(order.get("paymentId")) : null;
    const post = await tx.get(postRef);
    const payment = paymentRef ? await tx.get(paymentRef) : null;
    const shipment = await tx.get(shipmentRef);
    if (!shipment.exists) fail("not-found", "Không tìm thấy vận đơn.");
    buyerId = order.get("buyerId"); sellerId = order.get("sellerId");
    const nextOrderStatus = status === "DELIVERED" ? "DELIVERED" : (order.get("orderType") === "PAID" ? "REFUNDED" : "CANCELLED");
    tx.update(orderRef, { shippingStatus: status, orderStatus: nextOrderStatus, ...(status === "RETURNED" && order.get("orderType") === "PAID" ? { paymentStatus: "REFUNDED" } : {}), updatedAt: FieldValue.serverTimestamp() });
    tx.update(shipmentRef, { status, updatedAt: FieldValue.serverTimestamp(), events: FieldValue.arrayUnion({ status, description, createdAt: new Date().toISOString() }) });
    writeOrderEvent(tx, orderRef, order.get("orderStatus"), nextOrderStatus, "SIMULATED_CARRIER", description || status);
    if (status === "RETURNED") {
      if (post.exists) tx.update(postRef, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
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
  const orderRef = db.collection("orders").doc(orderId);
  let buyerId; let sellerId; let postId;
  await db.runTransaction(async (tx) => {
    const order = await tx.get(orderRef);
    if (!order.exists) fail("not-found", "Không tìm thấy đơn hàng.");
    buyerId = order.get("buyerId"); sellerId = order.get("sellerId"); postId = order.get("postId");
    const current = order.get("orderStatus");
    const postRef = db.collection("posts").doc(postId);
    const post = nextStatus === "COMPLETED" && order.get("orderType") === "FREE" ? await tx.get(postRef) : null;
    const sellerSteps = { CONFIRMED: "PREPARING", PREPARING: "READY_TO_SHIP" };
    const buyerSteps = { DELIVERED: "COMPLETED" };
    const allowed = (uid === sellerId && sellerSteps[current] === nextStatus)
      || (uid === buyerId && buyerSteps[current] === nextStatus);
    if (!allowed) fail("permission-denied", "Chuyển trạng thái đơn hàng không hợp lệ.");
    const patch = { orderStatus: nextStatus, updatedAt: FieldValue.serverTimestamp() };
    if (nextStatus === "COMPLETED") patch.shippingStatus = "DELIVERED";
    writeOrderEvent(tx, orderRef, current, nextStatus, uid);
    tx.update(orderRef, patch);
    if (nextStatus === "COMPLETED" && order.get("orderType") === "FREE") {
      if (post && post.exists) tx.update(postRef, { status: "GIVEN", updatedAt: FieldValue.serverTimestamp() });
    }
    if (nextStatus === "COMPLETED" && order.get("orderType") === "PAID") {
      tx.update(db.collection("moneyTransactions").doc(`received_${orderId}`), { status: "RELEASED", releasedAt: FieldValue.serverTimestamp() });
      const moneyRef = db.collection("moneyTransactions").doc(`payout_${orderId}`);
      tx.set(moneyRef, { orderId, paymentId: order.get("paymentId"), type: "SELLER_PAYOUT", amount: order.get("price"), mode: "SIMULATED", idempotencyKey: `payout_${orderId}`, createdAt: FieldValue.serverTimestamp() }, { merge: true });
    }
  });
  const recipient = uid === buyerId ? sellerId : buyerId;
  await notify(recipient, { type: "ORDER_UPDATED", title: "Đơn hàng được cập nhật", body: `Đơn ${orderId}: ${nextStatus}`, orderId });
  return { ok: true };
});

exports.updateShipmentFromCarrier = onRequest({ secrets: [shipmentWebhookToken] }, async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("Method not allowed");
  if (!validShipmentWebhook(req)) return res.status(401).send("Unauthorized");
  const { orderId, trackingCode, status, description } = req.body || {};
  if (!orderId || !trackingCode || !["IN_TRANSIT", "DELIVERED", "RETURNED"].includes(status)) return res.status(400).send("Invalid event");
  const normalizedOrderId = String(orderId);
  if (normalizedOrderId.includes("/")) return res.status(400).send("Invalid orderId");
  const orderRef = db.collection("orders").doc(normalizedOrderId);
  const shipmentRef = db.collection("shipments").doc(normalizedOrderId);
  let buyerId; let duplicateUpdate = false;
  try {
    await db.runTransaction(async (tx) => {
      const [order, shipment] = await Promise.all([tx.get(orderRef), tx.get(shipmentRef)]);
      if (!order.exists || !shipment.exists || shipment.get("trackingCode") !== trackingCode) throw new Error("Shipment not found");
      buyerId = order.get("buyerId");
      const currentStatus = order.get("shippingStatus");
      if (currentStatus === status) { duplicateUpdate = true; return; }
      if (status === "DELIVERED" && currentStatus !== "IN_TRANSIT") throw new Error("Invalid shipment transition");
      if (status === "RETURNED" && currentStatus !== "IN_TRANSIT") throw new Error("Invalid shipment transition");
      const postRef = status === "RETURNED" ? db.collection("posts").doc(order.get("postId")) : null;
      const post = postRef ? await tx.get(postRef) : null;
      const paymentRef = status === "RETURNED" && order.get("paymentId") ? db.collection("payments").doc(order.get("paymentId")) : null;
      const payment = paymentRef ? await tx.get(paymentRef) : null;
      const orderStatus = status === "DELIVERED" ? "DELIVERED" : (status === "RETURNED" ? (order.get("orderType") === "PAID" ? "REFUNDED" : "CANCELLED") : "SHIPPING");
      const orderPatch = { shippingStatus: status, orderStatus, updatedAt: FieldValue.serverTimestamp() };
      if (status === "RETURNED") orderPatch.paymentStatus = order.get("orderType") === "PAID" ? "REFUNDED" : "NOT_REQUIRED";
      tx.update(orderRef, orderPatch);
      tx.update(shipmentRef, { status, updatedAt: FieldValue.serverTimestamp(), events: FieldValue.arrayUnion({ status, description: String(description || ""), createdAt: new Date().toISOString() }) });
      writeOrderEvent(tx, orderRef, order.get("orderStatus"), orderPatch.orderStatus, "MOCK_CARRIER", String(description || status));
      if (status === "RETURNED") {
        if (post.exists) tx.update(postRef, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
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
        if (status === "PAID") tx.update(postRef, { status: "SOLD", updatedAt: FieldValue.serverTimestamp() });
        else if (status === "FAILED" || status === "REFUNDED") tx.update(postRef, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
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
  const unpaid = await db.collection("orders").where("orderStatus", "==", "PENDING_PAYMENT")
    .where("paymentDeadline", "<=", now).limit(200).get();
  for (const orderDoc of unpaid.docs) {
    await db.runTransaction(async (tx) => {
      const order = await tx.get(orderDoc.ref);
      if (!order.exists || order.get("orderStatus") !== "PENDING_PAYMENT") return;
      const postRef = db.collection("posts").doc(order.get("postId"));
      const post = await tx.get(postRef);
      tx.update(orderDoc.ref, { orderStatus: "CANCELLED", paymentStatus: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
      writeOrderEvent(tx, orderDoc.ref, "PENDING_PAYMENT", "CANCELLED", "SYSTEM", "Quá hạn thanh toán 15 phút.");
      if (post.exists && post.get("reservedForOrderId") === orderDoc.id) tx.update(postRef, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
      if (order.get("paymentId")) tx.update(db.collection("payments").doc(order.get("paymentId")), { status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
    });
    await notify(orderDoc.get("buyerId"), { type: "ORDER_EXPIRED", title: "Đơn hàng đã hết hạn", body: `Đơn ${orderDoc.id} đã được mở lại vì chưa thanh toán.`, orderId: orderDoc.id });
  }
  const unpaidSellers = await db.collection("orders").where("orderStatus", "==", "CONFIRMED")
    .where("sellerResponseDeadline", "<=", now).limit(200).get();
  for (const orderDoc of unpaidSellers.docs) {
    await db.runTransaction(async (tx) => {
      const order = await tx.get(orderDoc.ref);
      if (!order.exists || order.get("orderStatus") !== "CONFIRMED") return;
      const postRef = db.collection("posts").doc(order.get("postId"));
      const post = await tx.get(postRef);
      tx.update(orderDoc.ref, { orderStatus: "REFUNDED", paymentStatus: "REFUNDED", updatedAt: FieldValue.serverTimestamp() });
      writeOrderEvent(tx, orderDoc.ref, "CONFIRMED", "REFUNDED", "SYSTEM", "Người bán không phản hồi trong 48 giờ.");
      tx.set(db.collection("refunds").doc(orderDoc.id), { orderId: orderDoc.id, paymentId: order.get("paymentId"), amount: order.get("price") + order.get("shippingFee"), reason: "Người bán không phản hồi trong 48 giờ", status: "COMPLETED", mode: "SIMULATED", createdAt: FieldValue.serverTimestamp() });
      if (order.get("paymentId")) tx.update(db.collection("payments").doc(order.get("paymentId")), { status: "REFUNDED", updatedAt: FieldValue.serverTimestamp() });
      tx.set(db.collection("moneyTransactions").doc(`refund_${orderDoc.id}`), { orderId: orderDoc.id, paymentId: order.get("paymentId"), type: "REFUND", amount: order.get("price") + order.get("shippingFee"), mode: "SIMULATED", idempotencyKey: `refund_${orderDoc.id}`, createdAt: FieldValue.serverTimestamp() });
      tx.update(db.collection("moneyTransactions").doc(`received_${orderDoc.id}`), { status: "REFUNDED", refundedAt: FieldValue.serverTimestamp() });
      if (post.exists && post.get("reservedForOrderId") === orderDoc.id) tx.update(postRef, { status: "ACTIVE", reservedForOrderId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
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
