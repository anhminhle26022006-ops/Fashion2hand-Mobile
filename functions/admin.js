const crypto = require("node:crypto");
const orderItems = require("./order-items");

module.exports = ({ onCall, db, getAuth, getStorage, FieldValue, fail, cleanText, cleanDocumentId, writeOrderEvent }) => {
  const collections = new Set(["users", "posts", "orders", "reports", "disputes", "verificationRequests", "moneyTransactions", "categories", "adminAudit"]);
  const stamp = () => FieldValue.serverTimestamp();
  async function admin(request) {
    if (!request.auth) fail("unauthenticated", "Vui lòng đăng nhập.");
    let user;
    try { user = await getAuth().getUser(request.auth.uid); }
    catch (error) { if (error.code === "auth/user-not-found") fail("permission-denied", "Tài khoản Admin không còn tồn tại."); throw error; }
    const profile = await db.doc(`users/${request.auth.uid}`).get();
    if (user.disabled || user.customClaims?.admin !== true || !profile.exists || profile.get("role") !== "ADMIN" || profile.get("accountStatus") !== "ACTIVE")
      fail("permission-denied", "Tài khoản không có quyền Admin đang hoạt động.");
    return request.auth.uid;
  }
  const json = value => value?.toDate ? value.toDate().toISOString() : Array.isArray(value) ? value.map(json)
    : value && typeof value === "object" ? Object.fromEntries(Object.entries(value).map(([key, item]) => [key, json(item)])) : value;
  const row = doc => ({ id: doc.id, ...json(doc.data()) });
  const configDefaults = { shippingFee: 15000, maxPrice: 2000000, requirePhoneBeforeSelling: false, autoHideReported: false, reportThreshold: 5, autoCompleteDays: 3 };
  async function validateVerificationEvidence(doc, user) {
    if (user.get("verificationRequestId") !== doc.id || user.get("verificationStatus") !== "PENDING")
      fail("failed-precondition", "Hồ sơ không phải hồ sơ xác minh hiện tại của người dùng.");
    const prefix = `${user.id}_`, key = doc.id.startsWith(prefix) ? doc.id.slice(prefix.length) : "";
    const paths = doc.get("evidencePaths"), bucketName = "fashion2hand.firebasestorage.app";
    if (!key || key.length > 128 || key.includes("/") || !/^[a-f0-9]{64}$/.test(doc.get("fingerprint") || "")
        || doc.get("evidenceBucket") !== bucketName || !Array.isArray(paths) || paths.length !== 3)
      fail("failed-precondition", "Hồ sơ phải được gửi qua luồng xác minh và có đủ ba ảnh.");
    const bucket = getStorage().bucket(bucketName);
    await Promise.all(["front", "back", "selfie"].map(async slot => {
      const pathPrefix = `verificationEvidence/${user.id}/${key}/${slot}-`;
      const matches = paths.filter(path => typeof path === "string" && path.startsWith(pathPrefix)
        && /^[a-f0-9]{64}\.jpg$/.test(path.slice(pathPrefix.length)));
      if (matches.length !== 1) fail("failed-precondition", "Ảnh hồ sơ không đúng loại hoặc không thuộc người dùng.");
      let metadata;
      try { [metadata] = await bucket.file(matches[0]).getMetadata(); }
      catch (error) {
        if (Number(error.code) !== 404) throw error;
        fail("failed-precondition", "Ảnh hồ sơ không còn tồn tại. Vui lòng gửi lại hồ sơ.");
      }
      if (metadata.contentType !== "image/jpeg" || !Number.isFinite(Number(metadata.size))
          || Number(metadata.size) < 100 || Number(metadata.size) > 1200000)
        fail("failed-precondition", "Ảnh hồ sơ không hợp lệ.");
    }));
  }
  return {
    adminSession: onCall(async request => {
      const uid = await admin(request); return { uid, email: (await getAuth().getUser(uid)).email || "", role: "ADMIN" };
    }),
    adminList: onCall(async request => {
      await admin(request);
      const name = cleanText(request.data.collection, 40, "Danh sách");
      if (!collections.has(name)) fail("invalid-argument", "Danh sách không hợp lệ.");
      const limit = request.data.limit ?? 50;
      if (!Number.isInteger(limit) || limit < 1 || limit > 100) fail("invalid-argument", "Giới hạn từ 1 đến 100.");
      let query = db.collection(name).orderBy("__name__").limit(limit + 1);
      if (request.data.cursor) query = query.startAfter(cleanDocumentId(request.data.cursor, "Con trỏ"));
      const docs = (await query.get()).docs; const more = docs.length > limit; const page = docs.slice(0, limit);
      return { records: page.map(row), nextCursor: more ? page.at(-1).id : "" };
    }),
    adminDashboard: onCall(async request => {
      await admin(request);
      const count = async query => (await query.count().get()).data().count;
      const [users, activeUsers, posts, activePosts, orders, reports, disputes, verifications] = await Promise.all([
        count(db.collection("users")), count(db.collection("users").where("accountStatus", "==", "ACTIVE")),
        count(db.collection("posts")), count(db.collection("posts").where("status", "==", "ACTIVE")), count(db.collection("orders")),
        count(db.collection("reports").where("status", "in", ["PENDING", "REVIEWING"])),
        count(db.collection("disputes").where("status", "in", ["OPEN", "WAITING_EVIDENCE"])),
        count(db.collection("verificationRequests").where("status", "==", "PENDING"))]);
      return { users, activeUsers, posts, activePosts, orders, reports, disputes, verifications, updatedAt: new Date().toISOString(), mode: process.env.PAYMENT_PROVIDER==='VNPAY'?'MIXED_SANDBOX':'SIMULATED', paymentProvider:process.env.PAYMENT_PROVIDER==='VNPAY'?'VNPAY':'SIMULATED' };
    }),
    adminDetail: onCall(async request => {
      await admin(request);
      const name = cleanText(request.data.collection, 40, "Danh sách"), id = cleanDocumentId(request.data.id, "Mã");
      if (!collections.has(name)) fail("invalid-argument", "Danh sách không hợp lệ.");
      const doc = await db.doc(`${name}/${id}`).get();
      if (!doc.exists) fail("not-found", "Không tìm thấy dữ liệu.");
      const result = { record: row(doc) };
      const orderId = name === "orders" ? id : name === "disputes" ? doc.get("orderId") : null;
      if (orderId) {
        const [order, events, shipment, money] = await Promise.all([db.doc(`orders/${orderId}`).get(), db.collection(`orders/${orderId}/statusEvents`).orderBy("createdAt").get(), db.doc(`shipments/${orderId}`).get(), db.collection("moneyTransactions").where("orderId", "==", orderId).get()]);
        result.order = order.exists ? row(order) : null; result.timeline = events.docs.map(row);
        result.shipment = shipment.exists ? row(shipment) : null; result.money = money.docs.map(row);
      }
      return result;
    }),
    adminEvidence: onCall(async request => {
      await admin(request);
      const name = cleanText(request.data.collection, 40, "Danh sách"), id = cleanDocumentId(request.data.id, "Mã");
      if (!["disputes", "verificationRequests", "reports", "posts"].includes(name)) fail("invalid-argument", "Loại bằng chứng không hợp lệ.");
      const doc = await db.doc(`${name}/${id}`).get();
      const path = cleanText(request.data.path, 600, "Ảnh");
      let bucketName = doc.get("evidenceBucket") || "fashion2hand.firebasestorage.app", objectPath = path;
      if (name === "posts") {
                // imageUrls của bài đăng là gs://bucket/posts/... (app ghi ref.toString()) hoặc download URL https.
                // Chỉ cho tải ảnh có trong imageUrls của chính bài đó và nằm trong thư mục posts/{id}/images,
                // để Admin xem được cả bài đã ẩn/gỡ/đang giữ mà Storage Rules không cho đọc trực tiếp.
                const gs = /^gs:\/\/([^/]+)\/(.+)$/.exec(path), http = /\/v0\/b\/([^/]+)\/o\/([^?]+)/.exec(path);
                const match = gs || http;
                objectPath = gs ? gs[2] : http ? decodeURIComponent(http[2]) : "";
        if (!doc.exists || !(doc.get("imageUrls") || []).includes(path) || !match || match[1] !== bucketName
            || !objectPath.startsWith(`posts/${id}/images/`) || objectPath.includes(".."))
          fail("permission-denied", "Ảnh không thuộc bài đăng này.");
      } else if (!doc.exists || !(doc.get("evidencePaths") || []).includes(path)) fail("permission-denied", "Ảnh không thuộc hồ sơ này.");
      const bucket = getStorage().bucket(bucketName);
      const file = bucket.file(objectPath); const [meta] = await file.getMetadata();
      if (!meta.contentType?.startsWith("image/") || Number(meta.size) >= 5 * 1024 * 1024) fail("failed-precondition", "Ảnh không hợp lệ.");
      const [bytes] = await file.download(); return { contentType: meta.contentType, base64: bytes.toString("base64") };
    }),
    adminSettings: onCall(async request => {
      await admin(request); const doc = await db.doc("systemConfig/active").get(); return { ...configDefaults, ...(doc.exists ? json(doc.data()) : {}), requirePhoneBeforeSelling: false, revision: doc.exists ? doc.get("revision") || 0 : 0 };
    }),
    adminMutate: onCall(async request => {
      const uid = await admin(request);
      const action = cleanText(request.data.action, 40, "Thao tác"), id = cleanDocumentId(request.data.id, "Mã");
      const requestId = cleanDocumentId(request.data.requestId, "Mã thao tác");
      const data = request.data.value || {}, note = cleanText(request.data.note || "", 2000, "Ghi chú", false);
      const hash = crypto.createHash("sha256").update(JSON.stringify({ action, id, data, note })).digest("hex");
      const audit = db.doc(`adminAudit/${uid}_${requestId}`);
      let authTarget = null;
      await db.runTransaction(async tx => {
        const [prior, currentAdmin] = await Promise.all([tx.get(audit), tx.get(db.doc(`users/${uid}`))]);
        if (currentAdmin.get("role") !== "ADMIN" || currentAdmin.get("accountStatus") !== "ACTIVE") fail("permission-denied", "Quyền Admin đã thay đổi.");
        if (prior.exists) { if (prior.get("hash") !== hash) fail("already-exists", "Mã thao tác đã được dùng."); if (action === "userLock") authTarget = { uid: id, disabled: data.locked }; return; }
        const auditData = { actorId: uid, action, targetId: id, note, hash, createdAt: stamp() };
        if (action === "settings") {
          const ref = db.doc("systemConfig/active"), before = await tx.get(ref);
          if (data.revision !== (before.exists ? before.get("revision") || 0 : 0)) fail("aborted", "Cấu hình đã thay đổi. Tải lại trước khi lưu.");
          for (const [key, min, max] of [["shippingFee", 0, 1000000], ["maxPrice", 10000, 1000000000], ["reportThreshold", 1, 20], ["autoCompleteDays", 1, 14]])
            if (!Number.isSafeInteger(data[key]) || data[key] < min || data[key] > max) fail("invalid-argument", `Cấu hình ${key} không hợp lệ.`);
          for (const key of ["autoHideReported", "requirePhoneBeforeSelling"]) if (typeof data[key] !== "boolean") fail("invalid-argument", `${key} phải là boolean.`);
          const saved = Object.fromEntries(Object.keys(configDefaults).map(key => [key, data[key]]));
          saved.requirePhoneBeforeSelling = false;
          tx.set(ref, { ...saved, revision: data.revision + 1, updatedAt: stamp(), updatedBy: uid });
          tx.set(db.doc("shippingTariffs/active"), { minFee: data.shippingFee, updatedAt: stamp() }, { merge: true });
          auditData.before = before.exists ? before.data() : {}; auditData.after = saved;
        } else {
          const names = { userLock: "users", userWarning: "users", postStatus: "posts", report: "reports", disputeEvidence: "disputes", disputeResolve: "disputes", verification: "verificationRequests", category: "categories" };
          if (!names[action]) fail("invalid-argument", "Thao tác không hợp lệ.");
          const ref = db.doc(`${names[action]}/${id}`), doc = await tx.get(ref);
          if (!doc.exists && action !== "category") fail("not-found", "Đối tượng không tồn tại.");
          auditData.before = doc.exists ? doc.data() : {};
          if (action === "userLock" || action === "userWarning") {
            if (id === uid || doc.get("role") === "ADMIN") fail("failed-precondition", "Không thao tác khóa/cảnh báo quản trị viên tại đây.");
            if (action === "userLock") {
              if (typeof data.locked !== "boolean") fail("invalid-argument", "Trạng thái khóa không hợp lệ.");
              tx.update(ref, { accountStatus: data.locked ? "LOCKED" : "ACTIVE", updatedAt: stamp() }); authTarget = { uid: id, disabled: data.locked };
            } else {
              if (note.length < 3) fail("invalid-argument", "Nhập nội dung cảnh báo.");
              tx.create(db.collection(`users/${id}/notifications`).doc(), { type: "ADMIN_WARNING", title: "Cảnh báo quản trị", body: note, read: false, createdAt: stamp() });
            }
          } else if (action === "postStatus") {
            if (!["ACTIVE", "HIDDEN", "REMOVED"].includes(data.status)) fail("invalid-argument", "Trạng thái bài đăng không hợp lệ.");
            if (doc.get("reservedForOrderId") || ["SOLD", "GIVEN"].includes(doc.get("status"))) fail("failed-precondition", "Không thay đổi bài đang giao dịch hoặc đã hoàn tất.");
            tx.update(ref, { status: data.status, moderatedBy: uid, moderationLocked: data.status !== "ACTIVE", updatedAt: stamp() });
          } else if (action === "report") {
            if (!["PENDING", "REVIEWING"].includes(doc.get("status"))) fail("failed-precondition", "Báo cáo đã xử lý.");
            if (!["REJECTED", "RESOLVED", "REVIEWING"].includes(data.status)) fail("invalid-argument", "Trạng thái không hợp lệ.");
            // Resolving records the decision; target moderation is an explicit separate action.
            tx.update(ref, { status: data.status, resolutionNote: note, reviewedBy: uid, updatedAt: stamp() });
            if (doc.get("type") === "SUPPORT" && doc.get("reporterId")) tx.create(db.collection(`users/${doc.get("reporterId")}/notifications`).doc(), {
              type: "SUPPORT_UPDATED", title: "Yêu cầu hỗ trợ được cập nhật", body: note || data.status, read: false, createdAt: stamp()
            });
          } else if (action === "verification") {
            if (doc.get("status") !== "PENDING" || typeof data.approve !== "boolean") fail("failed-precondition", "Hồ sơ không còn chờ duyệt.");
            if (!data.approve && note.length < 3) fail("invalid-argument", "Nhập lý do từ chối.");
            const userRef = db.doc(`users/${cleanDocumentId(doc.get("userId"), "Người dùng")}`), user = await tx.get(userRef);
            if (!user.exists) fail("not-found", "Không tìm thấy người dùng.");
            if (data.approve) await validateVerificationEvidence(doc, user);
            tx.update(ref, { status: data.approve ? "APPROVED" : "REJECTED", note, reviewedBy: uid, updatedAt: stamp() });
            tx.update(userRef, { verificationStatus: data.approve ? "VERIFIED" : "UNVERIFIED", updatedAt: stamp() });
            tx.create(db.collection(`users/${user.id}/notifications`).doc(), { type: "VERIFICATION_UPDATED", title: data.approve ? "Hồ sơ xác minh đã được duyệt" : "Hồ sơ xác minh cần gửi lại", body: note || "Danh tính đã được quản trị viên xác minh thủ công.", read: false, createdAt: stamp() });
          } else if (action === "disputeEvidence" || action === "disputeResolve") {
            if (!["OPEN", "WAITING_EVIDENCE"].includes(doc.get("status"))) fail("failed-precondition", "Khiếu nại đã xử lý.");
            const orderRef = db.doc(`orders/${cleanDocumentId(doc.get("orderId"), "Đơn hàng")}`), order = await tx.get(orderRef);
            if (!order.exists || !["SHIPPING", "DELIVERED"].includes(order.get("orderStatus"))) fail("failed-precondition", "Đơn không thể xử lý khiếu nại.");
            if (action === "disputeEvidence") {
              if (note.length < 3) fail("invalid-argument", "Nhập yêu cầu bổ sung.");
              tx.update(ref, { status: "WAITING_EVIDENCE", evidenceRequest: note, updatedAt: stamp(), reviewedBy: uid });
              tx.update(orderRef, { disputeStatus: "WAITING_EVIDENCE", updatedAt: stamp() });
            } else {
              if (!["REFUND", "RELEASE"].includes(data.resolution) || note.length < 3) fail("invalid-argument", "Chọn quyết định và ghi lý do.");
              const refund = data.resolution === "REFUND", free = order.get("orderType") === "FREE";
              const paymentRef = order.get("paymentId") ? db.doc(`payments/${order.get("paymentId")}`) : null;
              const payment = paymentRef ? await tx.get(paymentRef) : null;
              const held = await tx.get(db.doc(`moneyTransactions/received_${order.id}`));
              const posts = await orderItems.readOrderPosts(tx, order, db);
              if (!free && (!payment?.exists || payment.get("status") !== "PAID" || !held.exists || held.get("status") !== "HOLDING")) fail("failed-precondition", "Không có khoản tiền đang giữ để xử lý.");
              // These ledger operations are deliberately restricted to the current simulation.
              if (!free && payment.get("provider") !== "SIMULATED" && payment.get("provider") !== "MOCK") fail("failed-precondition", "Chưa hỗ trợ đối soát nhà cung cấp thật.");
              const next = refund ? "REFUNDED" : "COMPLETED";
              tx.update(ref, { status: "RESOLVED", resolution: data.resolution, resolutionNote: note, reviewedBy: uid, resolvedAt: stamp() });
              tx.update(orderRef, { orderStatus: next, disputeStatus: "RESOLVED", paymentStatus: free ? "NOT_REQUIRED" : refund ? "REFUNDED" : "RELEASED", updatedAt: stamp() });
              if (!free) {
                const amount = payment.get("amount");
                tx.update(paymentRef, { status: refund ? "REFUNDED" : "RELEASED", updatedAt: stamp() });
                tx.set(db.doc(`moneyTransactions/received_${order.id}`), { status: refund ? "REFUNDED" : "RELEASED", updatedAt: stamp() }, { merge: true });
                tx.set(db.doc(`${refund ? "refunds" : "payouts"}/${order.id}`), { orderId: order.id, paymentId: paymentRef.id, amount: refund ? amount : order.get("price"), status: "COMPLETED", mode: "SIMULATED", reason: note, createdAt: stamp() });
                tx.set(db.doc(`moneyTransactions/${refund ? "refund" : "payout"}_${order.id}`), { orderId: order.id, paymentId: paymentRef.id, amount: refund ? amount : order.get("price"), type: refund ? "REFUND" : "SELLER_PAYOUT", status: "COMPLETED", mode: "SIMULATED", createdAt: stamp() });
              }
              orderItems.writeOrderPosts(tx, posts, { status: refund ? "HIDDEN" : free ? "GIVEN" : "SOLD", reservedForOrderId: FieldValue.delete(), updatedAt: stamp() });
              writeOrderEvent(tx, orderRef, order.get("orderStatus"), next, uid, `Admin: ${note}`);
            }
          } else if (action === "category") {
            if (typeof data.enabled !== "boolean") fail("invalid-argument", "Trạng thái danh mục không hợp lệ.");
            const name = cleanText(data.name || doc.get("name"), 80, "Tên danh mục");
            tx.set(ref, { name, enabled: data.enabled, updatedAt: stamp() }, { merge: true });
          }
          auditData.after = { ...data, note };
        }
        tx.create(audit, auditData);
      });
      if (authTarget) {
        // A retry of an older request must not undo a later lock/unlock decision.
        // Reconcile Auth to the current authoritative profile, not the old payload.
        const current = await db.doc(`users/${authTarget.uid}`).get();
        if (!current.exists || current.get("role") === "ADMIN" || !["ACTIVE", "LOCKED"].includes(current.get("accountStatus")))
          fail("failed-precondition", "Hồ sơ đã thay đổi; không thể đồng bộ trạng thái Auth.");
        const disabled = current.get("accountStatus") === "LOCKED";
        await getAuth().updateUser(authTarget.uid, { disabled });
        if (disabled) await getAuth().revokeRefreshTokens(authTarget.uid);
      }
      return { ok: true, auditId: audit.id };
    })
  };
};