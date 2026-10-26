/** Trusted simulated settlement; both explicit receipt and deadline settlement share this transaction. */
module.exports = ({ db, FieldValue, fail, writeOrderEvent }) => async function settleDelivery(orderId, buyerUid = null) {
  return db.runTransaction(async tx => {
    const ref = db.doc(`orders/${orderId}`);
    const order = await tx.get(ref);
    if (!order.exists) { if (buyerUid) fail('not-found', 'Không tìm thấy đơn hàng.'); return false; }
    if (buyerUid && order.get('buyerId') !== buyerUid) fail('permission-denied', 'Chỉ người mua được xác nhận nhận hàng.');
    if (order.get('orderStatus') === 'COMPLETED') return false;
    const deadline = order.get('inspectionDeadline');
    if (!buyerUid && (!deadline || deadline.toMillis() > Date.now())) return false;
    if (order.get('orderStatus') !== 'DELIVERED') { if (buyerUid) fail('failed-precondition', 'Chưa có xác nhận giao hàng.'); return false; }
    const shipment = await tx.get(db.doc(`shipments/${orderId}`));
    const dispute = await tx.get(db.doc(`disputes/${orderId}`));
    const blocked = [order.get('disputeStatus'), dispute.exists ? dispute.get('status') : null]
      .some(status => ['OPEN', 'WAITING_EVIDENCE'].includes(status));
    if (blocked) { if (buyerUid) fail('failed-precondition', 'Tranh chấp đang mở; chưa thể giải ngân.'); return false; }
    if (order.get('paymentNeedsReview')) { if (buyerUid) fail('failed-precondition','Thanh toán cần đối soát.'); return false; }
    if (!shipment.exists || shipment.get('status') !== 'DELIVERED'
        || !(shipment.get('events') || []).some(event => event.status === 'DELIVERED')) {
      if (buyerUid) fail('failed-precondition', 'Thiếu bằng chứng giao hàng từ backend.'); return false;
    }
    const paid = order.get('orderType') === 'PAID';
    const payment = paid && order.get('paymentId') ? await tx.get(db.doc(`payments/${order.get('paymentId')}`)) : null;
    const receivedRef = db.doc(`moneyTransactions/received_${orderId}`), payoutRef = db.doc(`moneyTransactions/payout_${orderId}`);
    const received = paid ? await tx.get(receivedRef) : null, payout = paid ? await tx.get(payoutRef) : null;
    const postRef = db.doc(`posts/${order.get('postId')}`), post = !paid ? await tx.get(postRef) : null;
    if (paid && (order.get('paymentStatus') !== 'PAID' || !payment?.exists || payment.get('status') !== 'PAID'
        || payment.get('orderId') !== orderId || !received.exists || received.get('status') !== 'HOLDING' || payout.exists)) {
      if (buyerUid) fail('failed-precondition', 'Thanh toán hoặc bút toán không đủ điều kiện giải ngân.'); return false;
    }
    const now = FieldValue.serverTimestamp();
    tx.update(ref, { orderStatus: 'COMPLETED', shippingStatus: 'DELIVERED', completedAt: now,
      ...(buyerUid ? { buyerConfirmedAt: now } : {}), updatedAt: now });
    writeOrderEvent(tx, ref, 'DELIVERED', 'COMPLETED', buyerUid || 'SYSTEM', buyerUid ? 'Người mua xác nhận nhận hàng.' : 'Đã hết thời hạn kiểm tra 3 ngày, không có tranh chấp.');
    if (paid) {
      if (require('./payment-policy').isVnpay(order)) {
        tx.update(ref, {payoutStatus:'REVIEW_REQUIRED'});
        tx.create(payoutRef, {orderId,paymentId:payment.id,type:'SELLER_PAYOUT',amount:order.get('price'),provider:'VNPAY',environment:'sandbox',mode:'VNPAY_SANDBOX',status:'REVIEW_REQUIRED',createdAt:now});
        for(const uid of [order.get('buyerId'),order.get('sellerId')])tx.create(db.doc(`users/${uid}/notifications/settled_${orderId}`),{type:'ORDER_COMPLETED',title:'Đơn thử nghiệm hoàn tất',body:'Chưa chuyển tiền cho người bán; cần đối soát thủ công.',orderId,read:false,createdAt:now});
        return true;
      }
      tx.update(receivedRef, { status: 'RELEASED', releasedAt: now });
      tx.create(payoutRef, { orderId, paymentId: payment.id, type: 'SELLER_PAYOUT', amount: order.get('price'),
        mode: 'SIMULATED', status: 'PAYOUT_COMPLETED_SIMULATED', idempotencyKey: `payout_${orderId}`, createdAt: now });
    } else if (post?.exists) tx.update(postRef, { status: 'GIVEN', updatedAt: now });
    for (const uid of [order.get('buyerId'), order.get('sellerId')]) tx.create(db.doc(`users/${uid}/notifications/settled_${orderId}`), {
      type: 'ORDER_COMPLETED', title: 'Đơn hàng hoàn tất', body: paid ? 'Đã ghi giải ngân mô phỏng; không chuyển tiền thật.' : 'Đơn nhận đồ đã hoàn tất.',
      orderId, read: false, createdAt: now
    });
    return true;
  });
};
