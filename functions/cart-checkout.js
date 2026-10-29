const crypto = require('node:crypto');
module.exports = ({ db, onCall, uidOf, cleanDocumentId, cleanText, fail, FieldValue, shippingFeeVnd, validatedAddress, postOwnerId, postType, orderPostSnapshot, writeOrderEvent, requireVerifiedAccount }) => {
  function idsOf(data) {
    if (!Array.isArray(data.postIds) || !data.postIds.length || data.postIds.length > 20) fail('invalid-argument', 'Chọn từ 1 đến 20 món đồ.');
    const ids = data.postIds.map(id => cleanDocumentId(id, 'Bài đăng')).sort();
    if (new Set(ids).size !== ids.length) fail('invalid-argument', 'Mỗi món đồ chỉ được chọn một lần.');
    return ids;
  }
  function group(posts, uid, fee) {
    const groups = new Map();
    for (const post of posts) {
      const sellerId = post.exists ? postOwnerId(post) : '';
      if (!post.exists || postType(post) !== 'SALE' || post.get('status') !== 'ACTIVE' || post.get('reservedForOrderId') || !sellerId || sellerId === uid)
        fail('failed-precondition', `Món ${post.id} không còn khả dụng hoặc không được mua.`);
      const price = post.get('price');
      if (!Number.isSafeInteger(price) || price <= 0) fail('failed-precondition', 'Giá sản phẩm không hợp lệ.');
      if (!groups.has(sellerId)) groups.set(sellerId, { sellerId, items: [], price: 0, shippingFee: fee });
      const target = groups.get(sellerId);
      target.price += price;
      if (!Number.isSafeInteger(target.price + fee)) fail('failed-precondition', 'Tổng tiền vượt giới hạn.');
      target.items.push({ postId: post.id, quantity: 1, price, ...orderPostSnapshot(post) });
    }
    return [...groups.values()].sort((a,b) => a.sellerId.localeCompare(b.sellerId));
  }
  const getCartCheckoutQuote = onCall(async request => {
    const uid = uidOf(request), ids = idsOf(request.data), fee = await shippingFeeVnd();
    const posts = await db.getAll(...ids.map(id => db.doc(`posts/${id}`)));
    const groups = group(posts, uid, fee);
    return { type: 'SALE', title: `${ids.length} món · ${groups.length} người bán`, groups,
      price: groups.reduce((sum,g) => sum+g.price,0), shippingFee: fee*groups.length,
      total: groups.reduce((sum,g) => sum+g.price+g.shippingFee,0), currency: 'VND', mode: 'SIMULATED' };
  });
  const createCartOrders = onCall(async request => {
    await requireVerifiedAccount(request);
    const uid = uidOf(request), ids = idsOf(request.data);
    const addressId = cleanDocumentId(request.data.addressId,'Địa chỉ nhận');
    const key = cleanText(request.data.idempotencyKey,128,'Mã yêu cầu');
    const fee = await shippingFeeVnd();
    const requestId = crypto.createHash('sha256').update(`${uid}:${key}`).digest('hex');
    const requestRef = db.doc(`orderRequests/cart_${requestId}`);
    return db.runTransaction(async tx => {
      const previous = await tx.get(requestRef);
      if (previous.exists) {
        if (JSON.stringify(previous.get('postIds')) !== JSON.stringify(ids) || previous.get('addressId') !== addressId)
          fail('already-exists','Mã yêu cầu đã dùng cho giỏ hàng hoặc địa chỉ khác.');
        return { orderIds: previous.get('orderIds') };
      }
      const posts = await Promise.all(ids.map(id => tx.get(db.doc(`posts/${id}`))));
      const cart = await Promise.all(ids.map(id => tx.get(db.doc(`users/${uid}/cartItems/${id}`))));
      if (cart.some(item => !item.exists)) fail('failed-precondition','Một món đã bị xóa khỏi giỏ. Vui lòng tải lại.');
      const address = await tx.get(db.doc(`users/${uid}/addresses/${addressId}`));
      const shippingAddress = validatedAddress(address), groups = group(posts,uid,fee);
      const total = groups.reduce((sum,g) => sum+g.price+g.shippingFee,0);
      if (!Number.isSafeInteger(total) || request.data.expectedTotal !== total)
        fail('failed-precondition','Giá hoặc phí giao hàng đã thay đổi. Vui lòng tải lại báo giá.');
      const orderIds = [], now = FieldValue.serverTimestamp();
      for (const entry of groups) {
        const orderId = `${requestId}_${crypto.createHash('sha256').update(entry.sellerId).digest('hex').slice(0,16)}`;
        const orderRef = db.doc(`orders/${orderId}`), paymentRef = db.doc(`payments/${orderId}`);
        orderIds.push(orderId);
        const first = entry.items[0];
        tx.create(orderRef, { orderType: 'PAID', buyerId: uid, sellerId: entry.sellerId, postId: first.postId,
          items: entry.items, itemCount: entry.items.length, schemaVersion: 2, price: entry.price, subtotal: entry.price,
          shippingFee: entry.shippingFee, totalAmount: entry.price+entry.shippingFee,
          postSnapshot: { ...first, title: first.title + (entry.items.length > 1 ? ` (+${entry.items.length-1} món)` : '') },
          shippingAddress, orderStatus: 'PENDING_PAYMENT', paymentStatus: 'PENDING', shippingStatus: 'READY', paymentId: paymentRef.id,
          trackingCode: '', paymentDeadline: new Date(Date.now()+15*60*1000), createdAt: now, updatedAt: now });
        tx.create(paymentRef, { orderId, buyerId: uid, sellerId: entry.sellerId, amount: entry.price+entry.shippingFee,
          currency: 'VND', status: 'PENDING', provider: 'SIMULATED', createdAt: now, updatedAt: now });
        writeOrderEvent(tx,orderRef,'','PENDING_PAYMENT',uid,'Đơn nhiều món được tạo theo người bán; hạn thanh toán 15 phút.');
        for (const item of entry.items) {
          tx.update(db.doc(`posts/${item.postId}`),{ status: 'RESERVED', reservedForOrderId: orderId, updatedAt: now });
          tx.delete(db.doc(`users/${uid}/cartItems/${item.postId}`));
        }
        for (const recipient of [uid,entry.sellerId]) tx.create(db.doc(`users/${recipient}/notifications/created_${orderId}`), {
          type: 'ORDER_AWAITING_PAYMENT', title: 'Đơn hàng mới', body: `${entry.items.length} món đang chờ thanh toán mô phỏng.`, orderId, read: false, createdAt: now });
      }
      tx.create(requestRef,{ buyerId:uid, postIds:ids, addressId, orderIds, createdAt:now });
      return { orderIds };
    });
  });
  return { getCartCheckoutQuote, createCartOrders };
};
