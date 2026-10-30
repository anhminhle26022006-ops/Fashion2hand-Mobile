'use strict';
const crypto = require('node:crypto');

// Một cặp người dùng = một phòng chat chuẩn. Phòng cũ (tạo theo từng bài đăng) được gộp vào phòng chuẩn:
// chép toàn bộ tin nhắn sang, cộng số tin chưa đọc, rồi đánh dấu mergedInto. Chạy lại nhiều lần vẫn an toàn.
module.exports = ({ db, FieldValue }) => {
  const pairChatId = (a, b) => crypto.createHash('sha256').update(`${[a, b].sort().join(':')}:`).digest('hex');
  const millis = value => value?.toMillis?.() || 0;
  const peerOf = (doc, uid) => {
    const ids = doc.get('participantIds');
    return Array.isArray(ids) && ids.length === 2 && ids.includes(uid) ? ids.find(id => id !== uid) : null;
  };
  const roomsOf = async uid => (await db.collection('chats').where('participantIds', 'array-contains', uid).get()).docs;

  async function copyMessages(from, to) {
    const messages = await from.collection('messages').get();
    for (let i = 0; i < messages.docs.length; i += 400) {
      const batch = db.batch();
      // Giữ nguyên mã tin nhắn: chép lại lần nữa chỉ ghi đè đúng tin đó, không sinh tin trùng.
      for (const message of messages.docs.slice(i, i + 400)) batch.set(to.collection('messages').doc(message.id), message.data());
      await batch.commit();
    }
  }

  async function mergePair(uid, peerId, rooms) {
    const members = [uid, peerId].sort();
    const target = db.collection('chats').doc(pairChatId(uid, peerId));
    const byUpdated = [...rooms].sort((a, b) => millis(b.get('updatedAt')) - millis(a.get('updatedAt')));
    const created = rooms.map(room => room.get('createdAt')).filter(Boolean).sort((a, b) => millis(a) - millis(b))[0];
    await db.runTransaction(async tx => {
      const snap = await tx.get(target);
      if (!snap.exists) tx.create(target, {
        participantIds: members, postId: byUpdated[0]?.get('postId') || null,
        createdAt: created || FieldValue.serverTimestamp(), updatedAt: byUpdated[0]?.get('updatedAt') || FieldValue.serverTimestamp(),
        lastMessage: '', lastMessageAt: null, unreadCounts: { [members[0]]: 0, [members[1]]: 0 }
      });
    });
    for (const room of rooms) {
      if (room.id === target.id || room.get('mergedInto')) continue;
      await copyMessages(room.ref, target);
      await db.runTransaction(async tx => {
        const [old, current] = await Promise.all([tx.get(room.ref), tx.get(target)]);
        if (!old.exists || old.get('mergedInto')) return;
        const update = {};
        for (const member of members) {
          const unread = Number((old.get('unreadCounts') || {})[member] || 0);
          if (unread > 0) update[`unreadCounts.${member}`] = FieldValue.increment(unread);
        }
        if (millis(old.get('lastMessageAt')) > millis(current.get('lastMessageAt'))) {
          update.lastMessage = old.get('lastMessage') || ''; update.lastMessageAt = old.get('lastMessageAt');
        }
        if (millis(old.get('updatedAt')) > millis(current.get('updatedAt'))) {
          update.updatedAt = old.get('updatedAt');
          if (old.get('postId')) update.postId = old.get('postId');
        }
        if (Object.keys(update).length) tx.update(target, update);
        tx.update(room.ref, { mergedInto: target.id, lastMessage: '', lastMessageAt: null, unreadCounts: { [members[0]]: 0, [members[1]]: 0 } });
      });
    }
    return target.id;
  }

  /** Phòng chuẩn của cặp (tạo nếu chưa có), sau khi đã gộp mọi phòng cũ của cặp đó. */
  async function chatFor(uid, peerId) {
    return mergePair(uid, peerId, (await roomsOf(uid)).filter(doc => peerOf(doc, uid) === peerId));
  }

  /** Gộp toàn bộ phòng cũ của một người dùng. Trả về số cặp vừa được gộp. */
  async function mergeAllFor(uid) {
    const groups = new Map();
    for (const doc of await roomsOf(uid)) {
      const peerId = peerOf(doc, uid);
      if (!peerId) continue;
      if (!groups.has(peerId)) groups.set(peerId, []);
      groups.get(peerId).push(doc);
    }
    let merged = 0;
    for (const [peerId, rooms] of groups) {
      if (rooms.every(room => room.id === pairChatId(uid, peerId) || room.get('mergedInto'))) continue;
      await mergePair(uid, peerId, rooms); merged++;
    }
    return merged;
  }

  return { pairChatId, chatFor, mergeAllFor };
};