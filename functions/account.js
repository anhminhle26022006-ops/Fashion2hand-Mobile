const { canonicalAddress } = require("./address-catalog");
const crypto = require("node:crypto");

/** Owner account operations shared by the profile, checkout and Admin screens. */
module.exports = ({ onCall, db, getStorage, FieldValue, uidOf, cleanText, cleanDocumentId, fail }) => {
  const stamp = () => FieldValue.serverTimestamp();
  const hash = value => crypto.createHash("sha256").update(value).digest("hex");
  const banks = ["Vietcombank", "Techcombank", "MB Bank", "BIDV", "ACB"];
  const verificationView = doc => doc?.exists ? {
    id: doc.id, status: doc.get("status"), legalName: doc.get("legalName") || "",
    documentType: doc.get("documentType") || "", note: doc.get("note") || ""
  } : null;
  return {
    getChatContext: onCall(async request => {
      const uid = uidOf(request), chatId = cleanDocumentId(request.data.chatId, "Cuộc trò chuyện");
      const chat = await db.doc(`chats/${chatId}`).get(), participants = chat.get("participantIds");
      if (!chat.exists || !Array.isArray(participants) || !participants.includes(uid)) fail("permission-denied", "Bạn không thuộc cuộc trò chuyện này.");
      const peerId = participants.find(id => id !== uid), postId = chat.get("postId");
      const [peer, post] = await Promise.all([db.doc(`users/${peerId}`).get(), postId ? db.doc(`posts/${postId}`).get() : null]);
      return { peerId, name: peer.exists && peer.get("accountStatus") === "ACTIVE" ? peer.get("name") || "Thành viên" : "Tài khoản không còn hoạt động",
        photoUrl: peer.exists && peer.get("accountStatus") === "ACTIVE" ? peer.get("photoUrl") || "" : "",
                role: post?.exists ? post.get("ownerId") === uid ? "sell" : "buy" : "other",
                postTitle: post?.exists ? String(post.get("title") || "") : "" };
    }),
    getAccountData: onCall(async request => {
      const uid = uidOf(request);
      const [user, payout, tickets] = await Promise.all([
        db.doc(`users/${uid}`).get(), db.doc(`privateAccounts/${uid}`).get(),
        db.collection("reports").where("reporterId", "==", uid).get()
      ]);
      const requestId = user.get("verificationRequestId");
      const verification = requestId ? await db.doc(`verificationRequests/${requestId}`).get() : null;
      return {
        verification: verification && verification.get("userId") === uid ? verificationView(verification) : null,
        verificationStatus: user.get("verificationStatus") || "UNVERIFIED",
        payout: payout.exists ? { bank: payout.get("bank"), holder: payout.get("holder"), accountNumber: payout.get("accountNumber"), verified: false } : null,
        tickets: tickets.docs.filter(doc => doc.get("type") === "SUPPORT").sort((a, b) => (b.get("createdAt")?.toMillis() || 0) - (a.get("createdAt")?.toMillis() || 0)).slice(0, 20)
          .map(doc => ({ id: doc.id, title: doc.get("title"), status: doc.get("status"), resolutionNote: doc.get("resolutionNote") || "" }))
      };
    }),
    saveAddress: onCall(async request => {
      const uid = uidOf(request), id = cleanDocumentId(request.data.id, "Mã địa chỉ");
      const source = request.data.address || {}, address = {};
      for (const [field, max] of [["recipient", 100], ["phone", 24], ["street", 300]])
        address[field] = cleanText(source[field], max, field);
      if (source.provinceCode || source.wardCode || source.addressVersion) Object.assign(address, canonicalAddress(source, fail));
      else { // Existing clients can still save legacy addresses during the app rollout.
        for (const key of ["province", "district", "ward"]) address[key] = cleanText(source[key], 100, key);
      }
      if (!/^\+?[0-9 ()-]{9,20}$/.test(address.phone)) fail("invalid-argument", "Số điện thoại không hợp lệ.");
      if (typeof source.isDefault !== "boolean") fail("invalid-argument", "Chọn trạng thái mặc định.");
      address.label = source.label === "WORK" ? "WORK" : "HOME";
      const collection = db.collection(`users/${uid}/addresses`), ref = collection.doc(id);
      await db.runTransaction(async tx => {
        const saved = await tx.get(collection), prior = saved.docs.find(doc => doc.id === id);
        if (!prior && saved.size >= 50) fail("resource-exhausted", "Bạn chỉ có thể lưu tối đa 50 địa chỉ.");
        const isDefault = source.isDefault || saved.empty || saved.docs.every(doc => doc.id === id || !doc.get("isDefault"));
        if (isDefault) for (const doc of saved.docs) if (doc.id !== id && doc.get("isDefault")) tx.update(doc.ref, { isDefault: false, updatedAt: stamp() });
        tx.set(ref, { ...address, isDefault, createdAt: prior?.get("createdAt") || stamp(), updatedAt: stamp() });
      });
      return { id };
    }),
    deleteAddress: onCall(async request => {
      const uid = uidOf(request), id = cleanDocumentId(request.data.id, "Mã địa chỉ");
      const collection = db.collection(`users/${uid}/addresses`);
      await db.runTransaction(async tx => {
        const saved = await tx.get(collection), prior = saved.docs.find(doc => doc.id === id);
        if (!prior) return;
        tx.delete(prior.ref);
        const remaining = saved.docs.filter(doc => doc.id !== id);
        if (prior.get("isDefault") && remaining.length) tx.update(remaining[0].ref, { isDefault: true, updatedAt: stamp() });
      });
      return { ok: true };
    }),
    savePayoutAccount: onCall(async request => {
      const uid = uidOf(request), bank = cleanText(request.data.bank, 60, "Ngân hàng"),
        holder = cleanText(request.data.holder, 100, "Chủ tài khoản"), accountNumber = cleanText(request.data.accountNumber, 30, "Số tài khoản");
      if (!banks.includes(bank) || !/^[0-9]{6,30}$/.test(accountNumber) || holder.length < 2 || request.data.consent !== true)
        fail("invalid-argument", "Kiểm tra ngân hàng, chủ tài khoản, số tài khoản và xác nhận đồng ý.");
      await db.doc(`privateAccounts/${uid}`).set({ userId: uid, bank, holder, accountNumber, verified: false, consentAt: stamp(), updatedAt: stamp() });
      return { ok: true, verified: false };
    }),
    submitVerification: onCall(async request => {
      const uid = uidOf(request), key = cleanDocumentId(request.data.requestId, "Mã gửi hồ sơ");
      const legalName = cleanText(request.data.legalName, 100, "Họ tên"), birthDate = cleanText(request.data.birthDate, 10, "Ngày sinh"),
        documentType = cleanText(request.data.documentType, 40, "Giấy tờ"), identityNumber = cleanText(request.data.identityNumber, 20, "Số giấy tờ");
      const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(birthDate);
      const date = match ? new Date(`${match[3]}-${match[2]}-${match[1]}T00:00:00Z`) : null;
      if (!date || Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== `${match[3]}-${match[2]}-${match[1]}` || +match[3] < 1900 || date > new Date())
        fail("invalid-argument", "Ngày sinh phải có định dạng dd/MM/yyyy và tồn tại.");
      if (!["CCCD gắn chip", "CCCD mã vạch", "Hộ chiếu"].includes(documentType) || !/^[A-Za-z0-9]{6,20}$/.test(identityNumber)
          || request.data.accurate !== true || request.data.consent !== true) fail("invalid-argument", "Kiểm tra giấy tờ và các xác nhận.");
      const photoData = request.data.photos || {}, buffers = {};
      for (const slot of ["front", "back", "selfie"]) {
        const encoded = photoData[slot];
        if (typeof encoded !== "string" || encoded.length > 1700000) fail("invalid-argument", "Cần đủ ba ảnh JPEG, mỗi ảnh dưới 1,2 MB.");
        const bytes = Buffer.from(encoded, "base64");
        if (bytes.length < 100 || bytes.length > 1200000 || bytes[0] !== 0xff || bytes[1] !== 0xd8 || bytes[2] !== 0xff)
          fail("invalid-argument", "Ảnh giấy tờ không hợp lệ.");
        buffers[slot] = bytes;
      }
      const fingerprint = hash(JSON.stringify({ legalName, birthDate, documentType, identityNumber, photos: Object.fromEntries(Object.entries(buffers).map(([slot, bytes]) => [slot, hash(bytes)])) }));
      const ref = db.doc(`verificationRequests/${uid}_${key}`), userRef = db.doc(`users/${uid}`);
      const [prior, user] = await Promise.all([ref.get(), userRef.get()]);
      if (!user.exists) fail("failed-precondition", "Cần hoàn thiện hồ sơ người dùng.");
      if (prior.exists) {
        if (prior.get("fingerprint") !== fingerprint) fail("already-exists", "Mã gửi đã được dùng cho hồ sơ khác.");
        return { id: ref.id, status: prior.get("status") };
      }
      if (["PENDING", "VERIFIED"].includes(user.get("verificationStatus"))) fail("failed-precondition", "Hồ sơ đang chờ duyệt hoặc đã được xác minh.");
      const bucket = getStorage().bucket("fashion2hand.firebasestorage.app");
      const paths = await Promise.all(Object.entries(buffers).map(async ([slot, bytes]) => {
        const path = `verificationEvidence/${uid}/${key}/${slot}-${hash(bytes)}.jpg`;
        await bucket.file(path).save(bytes, { resumable: false, metadata: { contentType: "image/jpeg" } });
        return path;
      }));
      await db.runTransaction(async tx => {
        const [current, profile] = await Promise.all([tx.get(ref), tx.get(userRef)]);
        if (current.exists) {
          if (current.get("fingerprint") !== fingerprint) fail("already-exists", "Mã gửi đã được dùng.");
          return;
        }
        if (["PENDING", "VERIFIED"].includes(profile.get("verificationStatus"))) fail("failed-precondition", "Đã có hồ sơ xác minh.");
        tx.create(ref, { userId: uid, legalName, birthDate, documentType, identityNumber, fingerprint, evidencePaths: paths,
          evidenceBucket: bucket.name, status: "PENDING", createdAt: stamp(), updatedAt: stamp() });
        tx.update(userRef, { verificationStatus: "PENDING", verificationRequestId: ref.id, updatedAt: stamp() });
        tx.create(db.collection(`users/${uid}/notifications`).doc(), { type: "VERIFICATION_SUBMITTED", title: "Đã nhận hồ sơ xác minh", body: "Hồ sơ đã gửi và đang chờ quản trị viên duyệt.", read: false, createdAt: stamp() });
      });
      return { id: ref.id, status: "PENDING" };
    }),
    submitSupportTicket: onCall(async request => {
      const uid = uidOf(request), key = cleanDocumentId(request.data.requestId, "Mã yêu cầu"),
        title = cleanText(request.data.title, 160, "Tiêu đề"), description = cleanText(request.data.description, 3000, "Nội dung");
      if (title.length < 3 || description.length < 10) fail("invalid-argument", "Tiêu đề tối thiểu 3 ký tự, nội dung tối thiểu 10 ký tự.");
      const ref = db.doc(`reports/support_${uid}_${key}`), fingerprint = hash(JSON.stringify({ title, description }));
      await db.runTransaction(async tx => {
        const prior = await tx.get(ref);
        if (prior.exists) {
          if (prior.get("fingerprint") !== fingerprint) fail("already-exists", "Mã yêu cầu đã được sử dụng.");
          return;
        }
        tx.create(ref, { reporterId: uid, type: "SUPPORT", title, description, reason: title, targetType: "USER", targetId: uid,
          status: "PENDING", fingerprint, createdAt: stamp(), updatedAt: stamp() });
      });
      return { id: ref.id };
    })
  };
};
