// Kiểm duyệt cộng đồng: người dùng báo cáo vi phạm, hệ thống áp dụng chính sách tự ẩn trong systemConfig/active.

module.exports = ({ onCall, db, FieldValue, requireVerifiedAccount, fail, cleanText, cleanDocumentId, notify }) => {
  const stamp = () => FieldValue.serverTimestamp();
  const TARGETS = { POST: "posts", USER: "users" };
  const OPEN = ["PENDING", "REVIEWING"];
  const policyDefaults = { autoHideReported: false, reportThreshold: 5 };

  async function policy(read = ref => ref.get()) {
    const doc = await read(db.doc("systemConfig/active"));
    return { ...policyDefaults, ...(doc.exists ? doc.data() : {}) };
  }

  async function submitReport(uid, data) {
    const targetType = cleanText(data.targetType, 10, "Loại đối tượng");
    if (!TARGETS[targetType]) fail("invalid-argument", "Chỉ báo cáo bài đăng hoặc người dùng.");
    const targetId = cleanDocumentId(data.targetId, "Đối tượng");
    const reason = cleanText(data.reason, 120, "Lý do");
    const description = cleanText(data.description || "", 1000, "Mô tả", false);
    if (reason.length < 3) fail("invalid-argument", "Chọn lý do báo cáo.");

    // Chỉ tài khoản đã xác minh email mới báo cáo được, và mỗi người chỉ có một báo cáo cho mỗi đối tượng,
    // nên một tài khoản không thể tự đẩy số báo cáo lên ngưỡng.
    const ref = db.doc(`reports/${uid}_${targetType}_${targetId}`);
    let autoHidden = false, ownerId = "";
    await db.runTransaction(async tx => {
      const targetRef = db.doc(`${TARGETS[targetType]}/${targetId}`);
      const [prior, target, config, related] = await Promise.all([
        tx.get(ref), tx.get(targetRef), policy(r => tx.get(r)),
        tx.get(db.collection("reports").where("targetId", "==", targetId))]);
      if (!target.exists) fail("not-found", "Không tìm thấy đối tượng cần báo cáo.");
      ownerId = targetType === "POST" ? target.get("ownerId") || "" : targetId;
      if (ownerId === uid) fail("failed-precondition", "Không thể tự báo cáo chính mình.");
      if (prior.exists && OPEN.includes(prior.get("status"))) fail("already-exists", "Bạn đã báo cáo mục này, quản trị viên đang xem xét.");

      tx.set(ref, { reporterId: uid, targetType, targetId, reason, description, status: "PENDING",
        createdAt: stamp(), updatedAt: stamp() });

      const openReporters = new Set(related.docs
        .filter(doc => doc.id !== ref.id && doc.get("targetType") === targetType && OPEN.includes(doc.get("status")))
        .map(doc => doc.get("reporterId")));
      openReporters.add(uid);
      const threshold = Number.isSafeInteger(config.reportThreshold) ? config.reportThreshold : policyDefaults.reportThreshold;
      if (targetType === "POST" && config.autoHideReported === true && openReporters.size >= threshold
          && target.get("status") === "ACTIVE" && !target.get("reservedForOrderId")) {
        tx.update(targetRef, { status: "HIDDEN", moderationLocked: true, moderatedBy: "SYSTEM", autoHidden: true, updatedAt: stamp() });
        tx.create(db.collection("adminAudit").doc(), { actorId: "SYSTEM", action: "autoHidePost", targetId,
          note: `Tự ẩn khi đủ ${openReporters.size}/${threshold} báo cáo.`, before: { status: "ACTIVE" }, after: { status: "HIDDEN" }, createdAt: stamp() });
        autoHidden = true;
      }
    });
    if (autoHidden) await notify(ownerId, { type: "POST_AUTO_HIDDEN", title: "Bài đăng tạm ẩn",
      body: "Bài đăng của bạn bị nhiều người báo cáo nên tạm ẩn chờ quản trị viên xem xét.", postId: targetId });
    return { id: ref.id, status: "PENDING", autoHidden };
  }

  return {
    submitReport: onCall(async request => submitReport(await requireVerifiedAccount(request), request.data || {}))
  };
};
