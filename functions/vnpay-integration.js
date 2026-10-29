'use strict';
const {VnpayProvider,VnpayError,parseQuery,settings}=require('./vnpay-provider');
module.exports=ctx=>{
  const {exports:endpoints,onCall,onRequest,defineSecret,fail,uidOf}=ctx;
  const tmn=defineSecret('VNPAY_TMN_CODE'),secret=defineSecret('VNPAY_HASH_SECRET');
  const options={secrets:[tmn,secret],timeoutSeconds:60};
  const provider=()=>new VnpayProvider({tmnCode:tmn.value(),secret:secret.value()});
  const service=require('./vnpay-payments')({...ctx,provider});
  const safe=fn=>async r=>{try{return await fn(r);}catch(e){if(e instanceof VnpayError)fail(e.code,e.message,e.details);throw e;}};
  endpoints.createVnpayPayment=onCall(options,safe(service.create));
  endpoints.getVnpayPaymentStatus=onCall(service.status);
  endpoints.reconcileVnpayPayment=onCall(options,safe(service.reconcile));
  const capabilities=endpoints.getTransactionCapabilities;
  endpoints.getTransactionCapabilities=onCall(options,async r=>{
    uidOf(r);const result=await capabilities.run(r);
    if(process.env.PAYMENT_PROVIDER!=='VNPAY')return {...result,paymentProvider:'SIMULATED',vnpayConfigured:false};
    let ready=false;try{provider();ready=true;}catch{/* Only expose readiness, never credentials. */}
    return {...result,paymentProvider:'VNPAY',paymentEnvironment:'sandbox',paymentMode:'VNPAY_SANDBOX',vnpayConfigured:ready,simulatedPayments:false,refundMode:'MANUAL_REVIEW',payoutMode:'MANUAL_REVIEW'};
  });
  // Old quote/order paths do not capture a verified GHN snapshot. Fail closed.
  for(const name of ['createPaidOrder','createCartOrders']){
    const previous=endpoints[name];endpoints[name]=onCall({secrets:[defineSecret('GHN_TOKEN')],timeoutSeconds:120},async r=>{
      if(process.env.PAYMENT_PROVIDER==='VNPAY'){settings();if(process.env.SHIPPING_PROVIDER!=='GHN')fail('failed-precondition','VNPAY sandbox yêu cầu GHN staging đã cấu hình.');}
      return previous.run(r);
    });
  }
  endpoints.vnpayIpn=onRequest({...options,invoker:'public'},async(req,res)=>{
    res.set('Cache-Control','no-store');
    if(req.method!=='GET')return res.status(405).json({RspCode:'99',Message:'GET required'});
    let p,fields;
    try{p=provider();}catch{return res.status(200).json({RspCode:'99',Message:'Configuration unavailable'});}
    try{fields=parseQuery(req.originalUrl||req.url);if(!p.verify(fields))return res.status(200).json({RspCode:'97',Message:'Invalid signature'});}
    catch{return res.status(200).json({RspCode:'97',Message:'Invalid signature'});}
    if(fields.vnp_TmnCode!==p.tmnCode)return res.status(200).json({RspCode:'01',Message:'Order not found'});
    try{return res.status(200).json(await service.apply(p.normalize(fields),p));}
    catch(e){return res.status(200).json({RspCode:'99',Message:e instanceof VnpayError?'Invalid signed data':'Retry later'});}
  });
  endpoints.vnpayReturn=onRequest({...options,invoker:'public'},async(req,res)=>{
    res.set({'Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'"});
    if(req.method!=='GET')return res.status(405).send('GET required');
    let link='',valid=false,paid=false,notPaid=false;
    try{
      const p=provider(),fields=parseQuery(req.originalUrl||req.url);
      if(p.verify(fields)){
        const result=p.normalize(fields,{source:'RETURN'});
        const attempt=await ctx.db.doc(`paymentAttempts/${result.reference}`).get();
        if(attempt.exists){
          link=`fashion2hand://payment/${encodeURIComponent(attempt.get('orderId'))}`;valid=true;
          // VNPAY sandbox cannot always reach our IPN, and Cloud Functions cannot always reach QueryDR.
          // The Return query carries the same merchant-signed (HMAC-SHA512) fields as IPN, so it is recorded
          // through the shared idempotent apply(): amount, attempt, merchant and duplicate checks stay identical.
          try{await service.apply(result,p);}catch(e){console.warn('VNPAY Return apply deferred',{code:e?.code||e?.name});}
          const order=await ctx.db.doc(`orders/${attempt.get('orderId')}`).get();
          paid=order.get('paymentStatus')==='PAID';notPaid=!paid&&!result.success;
        }
      }
    }catch{/* No raw callback/error is rendered. */}
    const message=paid?'Đã xác nhận thanh toán thử nghiệm. Mở ứng dụng để tiếp tục.'
      :notPaid?'Giao dịch chưa thành công hoặc đã bị hủy. Mở ứng dụng để thử lại.'
      :valid?'Đang xác nhận thanh toán. Mở ứng dụng để xem trạng thái do server xác minh.':'Không xác minh được liên kết. Mở ứng dụng và xem lại đơn hàng.';
    return res.status(valid?200:400).type('html').send(`<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Fashion2hand · VNPAY Sandbox</title><body style="font:18px system-ui;background:#f8f7f1;color:#124536;padding:32px;max-width:600px;margin:auto"><h1>VNPAY Sandbox</h1><p>${message}</p><p>Thanh toán thử nghiệm (VNPAY Sandbox), không trừ tiền thật.</p>${valid?`<a href="${link}">Mở lại Fashion2hand</a>`:''}</body></html>`);
  });
};
