'use strict';
const crypto=require('node:crypto');
const {orderPostIds}=require('./order-items');
const {amountUnits,dateVn,clientIp}=require('./vnpay-provider');
const hash=value=>crypto.createHash('sha256').update(value).digest('hex');
const millis=value=>value?.toMillis?.()??(value instanceof Date?value.getTime():0);
const messages={'00':'Confirm Success','01':'Order not found','02':'Order already confirmed','04':'Invalid amount','97':'Invalid signature','99':'Unknown error'};
const ack=code=>({RspCode:code,Message:messages[code]});
module.exports=({db,FieldValue,fail,uidOf,cleanDocumentId,writeOrderEvent,provider,logger,now=()=>Date.now()})=>{
  const stamp=()=>FieldValue.serverTimestamp();
  const merchant=p=>hash(p.tmnCode);
  function checkAmount(order,payment) {
    const price=order.get('price'),fee=order.get('shippingFee'),total=price+fee;
    const items=order.get('items');
    if(Array.isArray(items)&&(items.length<1||items.length>20||new Set(items.map(i=>i.postId)).size!==items.length||
      items.some(i=>!Number.isSafeInteger(i.price)||i.price<=0||(i.quantity!=null&&i.quantity!==1)||(i.sellerId!=null&&i.sellerId!==order.get('sellerId')))||
      items.reduce((sum,i)=>sum+i.price,0)!==price||(order.get('itemCount')!=null&&order.get('itemCount')!==items.length)))
      fail('failed-precondition','Snapshot các món không khớp tổng tiền đơn.');
    if(!Number.isSafeInteger(price)||price<=0||!Number.isSafeInteger(fee)||fee<0||!Number.isSafeInteger(total)||
      (order.get('totalAmount')!=null&&order.get('totalAmount')!==total)||payment.get('amount')!==total||payment.get('currency')!=='VND')
      fail('failed-precondition','Snapshot số tiền/tiền tệ không hợp lệ; không tự đổi báo giá.');
    amountUnits(total);return total;
  }
  async function create(request) {
    const uid=uidOf(request),id=cleanDocumentId(request.data.orderId,'Đơn hàng'),p=provider(),ip=clientIp(request.rawRequest),fingerprint=merchant(p);
    const attemptRef=db.collection('paymentAttempts').doc(crypto.randomBytes(16).toString('hex'));
    const attempt=await db.runTransaction(async tx=>{
      const order=await tx.get(db.doc(`orders/${id}`));
      if(!order.exists||order.get('buyerId')!==uid)fail('permission-denied','Chỉ người mua được thanh toán đơn này.');
      if(order.get('orderType')!=='PAID'||order.get('paymentProvider')!=='VNPAY'||order.get('paymentMode')!=='VNPAY_SANDBOX'||order.get('paymentEnvironment')!=='sandbox')fail('failed-precondition','Đơn không được tạo cho VNPAY sandbox; không chuyển đơn mô phỏng cũ sang VNPAY.');
      if(order.get('paymentNeedsReview'))fail('failed-precondition','Đơn đang cần đối soát thanh toán; không tạo thêm phiên.');
      const deadline=millis(order.get('paymentDeadline'));
      if(order.get('orderStatus')!=='PENDING_PAYMENT'||order.get('paymentStatus')==='PAID'||deadline<now()+30000)fail('failed-precondition','Đơn không còn thời hạn để mở thanh toán.');
      const payment=await tx.get(db.doc(`payments/${order.get('paymentId')}`));
      if(!payment.exists||payment.get('orderId')!==id||payment.get('buyerId')!==uid||payment.get('sellerId')!==order.get('sellerId')||payment.get('provider')!=='VNPAY'||payment.get('environment')!=='sandbox'||payment.get('status')==='PAID')fail('failed-precondition','Payment không khớp đơn.');
      const amount=checkAmount(order,payment),ids=orderPostIds(order);
      if(!ids.length||ids.length>20)fail('failed-precondition','Danh sách món không hợp lệ.');
      const posts=await Promise.all(ids.map(postId=>tx.get(db.doc(`posts/${postId}`))));
      if(posts.some(post=>!post.exists||post.get('reservedForOrderId')!==id||post.get('status')!=='RESERVED'))fail('failed-precondition','Đơn không còn giữ đủ các món.');
      if(order.get('shippingProvider')!=='GHN'||order.get('shippingEnvironment')!=='staging'||order.get('shippingQuote')?.shippingFee!==order.get('shippingFee'))fail('failed-precondition','Cần snapshot báo giá GHN staging đã xác nhận.');
      const activeId=payment.get('activeAttemptId');
      const active=activeId?await tx.get(db.doc(`paymentAttempts/${activeId}`)):null;
      if(active?.exists&&active.get('status')==='PENDING'&&millis(active.get('expiresAt'))>now()+5000) {
        if(active.get('merchantFingerprint')!==fingerprint||active.get('amount')!==amount)fail('failed-precondition','Cấu hình merchant/snapshot đã đổi. Cần đối soát phiên đang có.');
        return active.data();
      }
      if((payment.get('attemptCount')||0)>=10)fail('resource-exhausted','Đơn đã có 10 phiên; cần đối soát trước khi thử thêm.');
      const data={reference:attemptRef.id,orderId:id,paymentId:payment.id,buyerId:uid,sellerId:order.get('sellerId'),provider:'VNPAY',environment:'sandbox',merchantFingerprint:fingerprint,
        amount,currency:'VND',status:'PENDING',ip,createDate:dateVn(new Date(now())),expireDate:dateVn(new Date(deadline)),expiresAt:new Date(deadline),createdAt:stamp(),updatedAt:stamp()};
      tx.create(attemptRef,data);tx.update(payment.ref,{activeAttemptId:attemptRef.id,attemptCount:(payment.get('attemptCount')||0)+1,updatedAt:stamp()});
      tx.update(order.ref,{activePaymentAttemptId:attemptRef.id,updatedAt:stamp()});return data;
    });
    // No network, credential, URL or signature is stored in public payment documents.
    return {orderId:id,attemptId:attempt.reference,paymentUrl:p.paymentUrl(attempt),expiresAt:new Date(millis(attempt.expiresAt)).toISOString(),environment:'sandbox'};
  }
  async function apply(result,p) {
    return db.runTransaction(async tx=>{
      const attemptRef=db.doc(`paymentAttempts/${result.reference}`),attempt=await tx.get(attemptRef);
      if(!attempt.exists||attempt.get('provider')!=='VNPAY'||attempt.get('environment')!=='sandbox'||attempt.get('merchantFingerprint')!==merchant(p))return ack('01');
      if(amountUnits(attempt.get('amount'))!==result.units)return ack('04');
      const order=await tx.get(db.doc(`orders/${attempt.get('orderId')}`)),payment=await tx.get(db.doc(`payments/${attempt.get('paymentId')}`));
      if(!order.exists||!payment.exists||payment.get('orderId')!==order.id||payment.get('provider')!=='VNPAY'||payment.get('environment')!=='sandbox'||order.get('paymentProvider')!=='VNPAY')return ack('01');
      if(payment.get('amount')!==attempt.get('amount')||order.get('price')+order.get('shippingFee')!==attempt.get('amount'))return ack('04');
      const eventId=hash(JSON.stringify([result.reference,result.transactionNo,result.responseCode,result.transactionStatus,result.payDate]));
      const eventRef=db.doc(`webhookEvents/vnpay_${eventId}`),event=await tx.get(eventRef);
      if(event.exists)return ack('02');
      const evidence={provider:'VNPAY',environment:'sandbox',reference:result.reference,orderId:order.id,transactionNo:result.transactionNo,
        responseCode:result.responseCode,transactionStatus:result.transactionStatus,payDate:result.payDate,source:result.source,createdAt:stamp()};
      if(!result.success) {
        // Non-final status/timeout is not a failure. Never downgrade accepted success.
        if(attempt.get('status')!=='SUCCEEDED')tx.update(attemptRef,{status:result.failed?'FAILED':attempt.get('status'),responseCode:result.responseCode,transactionStatus:result.transactionStatus,updatedAt:stamp()});
        if(attempt.get('status')!=='SUCCEEDED'&&(result.responseCode==='07'||['04','05','06','07','09'].includes(result.transactionStatus))) {
          tx.update(attemptRef,{needsReview:true,reviewReason:'NON_FINAL_FINANCIAL_STATUS'});
          tx.update(order.ref,{paymentNeedsReview:true,paymentReviewReason:'NON_FINAL_FINANCIAL_STATUS',updatedAt:stamp()});
        }
        tx.create(eventRef,{...evidence,status:'RECORDED'});return ack('00');
      }
      const transactionRef=db.doc(`vnpayTransactions/${hash(`${merchant(p)}:${result.transactionNo}`)}`),transaction=await tx.get(transactionRef);
      if(transaction.exists&&transaction.get('reference')!==result.reference){
        tx.create(eventRef,{...evidence,status:'TRANSACTION_REFERENCE_CONFLICT'});
        tx.update(attemptRef,{needsReview:true,reviewReason:'TRANSACTION_REFERENCE_CONFLICT'});
        tx.update(order.ref,{paymentNeedsReview:true});return ack('00');
      }
      if(transaction.exists){tx.create(eventRef,{...evidence,status:'DUPLICATE'});return ack('02');}
      const receivedRef=db.doc(`moneyTransactions/received_${order.id}`),received=await tx.get(receivedRef);
      const posts=await Promise.all(orderPostIds(order).map(id=>tx.get(db.doc(`posts/${id}`))));
      const alreadyPaid=payment.get('status')==='PAID'||Boolean(payment.get('successfulAttemptId'))||received.exists;
      const fulfill=!alreadyPaid&&order.get('orderStatus')==='PENDING_PAYMENT'&&millis(order.get('paymentDeadline'))>=now()&&
        posts.length>0&&posts.every(post=>post.exists&&post.get('reservedForOrderId')===order.id&&post.get('status')==='RESERVED');
      const review=alreadyPaid?'DUPLICATE_SUCCESS':fulfill?'':'LATE_OR_RELEASED_INVENTORY';
      tx.create(transactionRef,{...evidence,amount:attempt.get('amount'),needsReview:!!review});
      tx.create(eventRef,{...evidence,status:review||'PROCESSED'});
      tx.update(attemptRef,{status:'SUCCEEDED',transactionNo:attempt.get('transactionNo')||result.transactionNo,needsReview:!!review,updatedAt:stamp()});
      const receipt={orderId:order.id,paymentId:payment.id,attemptId:attempt.id,transactionNo:result.transactionNo,type:'PAYMENT_RECEIVED',amount:attempt.get('amount'),provider:'VNPAY',environment:'sandbox',mode:'VNPAY_SANDBOX',status:review?'REVIEW_REQUIRED':'HOLDING',createdAt:stamp()};
      if(alreadyPaid)tx.create(db.doc(`moneyTransactions/vnpay_extra_${eventId}`),receipt);
      else {
        tx.create(receivedRef,receipt);
        tx.update(payment.ref,{status:'PAID',successfulAttemptId:attempt.id,providerReference:result.transactionNo,verifiedAt:stamp(),needsReview:!!review,updatedAt:stamp()});
        tx.update(order.ref,{paymentStatus:'PAID',paymentVerifiedAt:stamp(),...(fulfill?{orderStatus:'CONFIRMED',sellerResponseDeadline:new Date(now()+48*3600000)}:{}),updatedAt:stamp()});
      }
      if(review)tx.update(order.ref,{paymentNeedsReview:true,paymentReviewReason:review,updatedAt:stamp()});
      if(fulfill)for(const post of posts)tx.update(post.ref,{status:'SOLD',updatedAt:stamp()});
      writeOrderEvent(tx,order.ref,order.get('orderStatus'),fulfill?'CONFIRMED':order.get('orderStatus'),'VNPAY_SANDBOX',review||'Thanh toán thử nghiệm đã được server xác minh.');
      for(const uid of [attempt.get('buyerId'),attempt.get('sellerId')])tx.create(db.doc(`users/${uid}/notifications/vnpay_${eventId}`),{type:'PAYMENT_UPDATED',title:'VNPAY Sandbox',body:review?'Đã nhận kết quả thanh toán thử; cần đối soát.':'Đã xác minh thanh toán thử nghiệm.',orderId:order.id,read:false,createdAt:stamp()});
      return ack('00');
    });
  }
  async function status(request) {
    const uid=uidOf(request),id=cleanDocumentId(request.data.orderId,'Đơn hàng'),order=await db.doc(`orders/${id}`).get();
    if(!order.exists||![order.get('buyerId'),order.get('sellerId')].includes(uid))fail('permission-denied','Không có quyền xem thanh toán.');
    const payment=order.get('paymentId')?await db.doc(`payments/${order.get('paymentId')}`).get():null;
    return {orderId:id,orderStatus:order.get('orderStatus'),paymentStatus:order.get('paymentStatus'),provider:payment?.get('provider')||'',environment:payment?.get('environment')||'',attemptId:payment?.get('activeAttemptId')||'',needsReview:order.get('paymentNeedsReview')===true};
  }
  async function reconcile(request) {
    const state=await status(request);
    // An IPN may have committed while the app was returning from the browser.
    if(state.paymentStatus==='PAID')return state;
    const p=provider();
    const ref=cleanDocumentId(request.data.attemptId||state.attemptId,'Phiên thanh toán');
    const attempt=await db.runTransaction(async tx=>{
      const doc=await tx.get(db.doc(`paymentAttempts/${ref}`));
      if(!doc.exists||doc.get('orderId')!==state.orderId||doc.get('merchantFingerprint')!==merchant(p))fail('permission-denied','Phiên không thuộc đơn/merchant.');
      const order=await tx.get(db.doc(`orders/${state.orderId}`));
      const retryAt=millis(order.get('paymentQueryRetryAfter'));
      if(retryAt>now())fail('resource-exhausted','VNPAY đang giới hạn tra cứu. Chờ hết thời gian rồi kiểm tra lại.',{reason:'QUERY_RATE_LIMITED',retryAfterSeconds:Math.ceil((retryAt-now())/1000)});
      if(millis(order.get('lastPaymentQueryAt'))>now()-60000)fail('resource-exhausted','Chờ 60 giây trước lần QueryDR tiếp theo.');
      tx.update(order.ref,{lastPaymentQueryAt:new Date(now())});return doc.data();
    });
    let result;
    try {result=await p.query(attempt);}
    catch(error){
      if(error instanceof require('./vnpay-provider').VnpayError&&error.details?.reason==='QUERY_RATE_LIMITED')await db.doc(`orders/${state.orderId}`).update({paymentQueryRetryAfter:new Date(now()+300000)});
      if(logger&&error instanceof require('./vnpay-provider').VnpayError)logger.warn('VNPAY QueryDR outcome',{referenceHash:hash(ref).slice(0,12),code:error.code,...error.details});
      throw error;
    }
    if(!result.pending)await apply(result,p);
    return status(request);
  }
  return {create,apply,status,reconcile};
};
module.exports.ack=ack;
