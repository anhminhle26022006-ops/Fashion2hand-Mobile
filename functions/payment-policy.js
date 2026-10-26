// Server-only financial policy. Sandbox receipt is not an escrow or a bank transfer.
function orderPaymentConfig(env=process.env) {
  if(env.PAYMENT_PROVIDER==='VNPAY') {
    require('./vnpay-provider').settings(env);
    return {provider:'VNPAY',environment:'sandbox',mode:'VNPAY_SANDBOX'};
  }
  if(env.PAYMENT_PROVIDER && env.PAYMENT_PROVIDER!=='SIMULATED')throw new Error('Unsupported PAYMENT_PROVIDER');
  return {provider:'SIMULATED',environment:'emulator',mode:'SIMULATED'};
}
const isVnpay = order => order.get('paymentProvider')==='VNPAY'||order.get('paymentMode')==='VNPAY_SANDBOX';
function requestRefund(tx,db,FieldValue,order,payment,reason) {
  const now=FieldValue.serverTimestamp(),orderId=order.id;
  tx.update(order.ref,{orderStatus:'CANCELLED',paymentStatus:'PAID',refundStatus:'REVIEW_REQUIRED',paymentNeedsReview:true,updatedAt:now});
  tx.set(db.doc(`refunds/${orderId}`),{orderId,paymentId:payment.id,amount:payment.get('amount'),provider:'VNPAY',environment:'sandbox',mode:'VNPAY_SANDBOX',status:'REVIEW_REQUIRED',reason,updatedAt:now},{merge:true});
  tx.set(db.doc(`moneyTransactions/received_${orderId}`),{status:'REFUND_REVIEW_REQUIRED',updatedAt:now},{merge:true});
}
module.exports={orderPaymentConfig,isVnpay,requestRefund};
