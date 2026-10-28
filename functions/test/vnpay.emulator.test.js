const {test,after}=require('node:test');
const assert=require('node:assert/strict');
const crypto=require('node:crypto');
// A hard local target and demo project prevent accidental live financial writes.
process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:8080';
process.env.GCLOUD_PROJECT='demo-fashion2hand-vnpay';
process.env.FIREBASE_CONFIG=JSON.stringify({projectId:process.env.GCLOUD_PROJECT});
process.env.FUNCTIONS_EMULATOR='true';
process.env.PAYMENT_PROVIDER='VNPAY';process.env.VNPAY_ENV='sandbox';
process.env.VNPAY_PAYMENT_URL='https://sandbox.vnpayment.vn/paymentv2/vpcpay.html';
process.env.VNPAY_RETURN_URL='https://us-central1-fashion2hand.cloudfunctions.net/vnpayReturn';
process.env.SHIPPING_PROVIDER='GHN';process.env.GHN_ENV='staging';process.env.GHN_TOKEN_ENV='staging';
process.env.VNPAY_TMN_CODE='TESTONLY';process.env.VNPAY_HASH_SECRET='emulator-test-secret-only';
const {initializeApp,deleteApp,getApps}=require('firebase-admin/app');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
const app=initializeApp({projectId:process.env.GCLOUD_PROJECT},'vnpay-tests'),db=getFirestore(app);
const {VnpayProvider,canonical,hmac,QUERY_RESPONSE_FIELDS,pipeData}=require('../vnpay-provider');
const provider=()=>new VnpayProvider({tmnCode:'TESTONLY',secret:'emulator-test-secret-only'});
const fail=(code,message)=>{const e=new Error(message);e.code=code;throw e;};
const uidOf=r=>{if(!r.auth?.uid)fail('unauthenticated','Sign in');return r.auth.uid;};
const cleanDocumentId=id=>{if(typeof id!=='string'||!/^[A-Za-z0-9_-]{1,128}$/.test(id))fail('invalid-argument','Invalid id');return id;};
const writeOrderEvent=(tx,ref,from,to,actor,reason)=>tx.create(ref.collection('statusEvents').doc(),{from,to,actor,reason,createdAt:FieldValue.serverTimestamp()});
const ctx={db,FieldValue,fail,uidOf,cleanDocumentId,writeOrderEvent,provider};
const service=require('../vnpay-payments')(ctx);
const req=(uid,data)=>({auth:{uid},data,rawRequest:{socket:{remoteAddress:'127.0.0.1'}}});
const prefix='vn'+Date.now(),transactionBase=crypto.randomInt(100000000,900000000);let sequence=0;
after(async()=>{for(const instance of getApps())await deleteApp(instance);});
async function fixture({legacy=false,count=2}={}) {
 const id=prefix+(++sequence),buyer=id+'B',seller=id+'S',posts=Array.from({length:count},(_,i)=>id+'P'+i);
 const now=FieldValue.serverTimestamp(),deadline=new Date(Date.now()+15*60000);
 const items=posts.map(postId=>({postId,title:'Test item',price:10000,quantity:1}));
 await Promise.all([db.doc(`users/${buyer}`).set({accountStatus:'ACTIVE'}),db.doc(`users/${seller}`).set({accountStatus:'ACTIVE'}),...posts.map(postId=>db.doc('posts/'+postId).set({status:'RESERVED',reservedForOrderId:id}))]);
 const pickup={recipient:'Test',phone:'0900000000',street:'Fixture',province:'Province',district:'District',ward:'Ward',ghn:{provinceId:1,districtId:2,wardCode:'3',environment:'staging'}};
 const quote={provider:'GHN',environment:'staging',shippingFee:15000,shopId:10,pickup,shippingAddress:pickup,service:{service_id:20,service_type_id:2},parcel:{weight:300,length:25,width:20,height:3},items};
 await db.doc('orders/'+id).set({buyerId:buyer,sellerId:seller,orderType:'PAID',postId:posts[0],...(legacy?{}:{schemaVersion:2,items,itemCount:count}),
  price:10000*count,shippingFee:15000,totalAmount:10000*count+15000,shippingProvider:'GHN',shippingEnvironment:'staging',shippingQuote:quote,
  paymentProvider:'VNPAY',paymentEnvironment:'sandbox',paymentMode:'VNPAY_SANDBOX',paymentId:id,paymentStatus:'PENDING',orderStatus:'PENDING_PAYMENT',paymentDeadline:deadline,createdAt:now});
 await db.doc('payments/'+id).set({orderId:id,buyerId:buyer,sellerId:seller,provider:'VNPAY',environment:'sandbox',amount:10000*count+15000,currency:'VND',status:'PENDING'});
 const attempt=await service.create(req(buyer,{orderId:id}));
 return {id,buyer,seller,posts,attempt,transactionNo:String(transactionBase+sequence),order:()=>db.doc('orders/'+id).get(),payment:()=>db.doc('payments/'+id).get()};
}
function callback(f,overrides={}) {
 const url=new URL(f.attempt.paymentUrl);
 const fields={vnp_TmnCode:'TESTONLY',vnp_TxnRef:f.attempt.attemptId,vnp_Amount:url.searchParams.get('vnp_Amount'),vnp_ResponseCode:'00',vnp_TransactionStatus:'00',vnp_TransactionNo:f.transactionNo,vnp_PayDate:'20261027011000',...overrides};
 fields.vnp_SecureHash=hmac('emulator-test-secret-only',canonical(fields));return fields;
}
const apply=(f,overrides={})=>service.apply(provider().normalize(callback(f,overrides)),provider());
const onCall=(options,fn)=>{if(typeof options==='function'){fn=options;options={};}return {run:fn,options};};
const onRequest=(options,fn)=>{fn.options=options;return fn;};
const endpoints={getTransactionCapabilities:{run:async()=>({shippingProvider:'GHN'})},createPaidOrder:{run:async()=>({})},createCartOrders:{run:async()=>({})}};
require('../vnpay-integration')({...ctx,exports:endpoints,onCall,onRequest,defineSecret:name=>({value:()=>process.env[name],name})});
async function http(name,fields,{method='GET',raw}={}) {
 let body,statusCode=200;const res={set:()=>res,status:code=>{statusCode=code;return res;},json:value=>{body=value;return res;},type:()=>res,send:value=>{body=value;return res;}};
 const query=canonical(fields)+'&vnp_SecureHash='+fields.vnp_SecureHash;
 await endpoints[name]({method,originalUrl:raw||'/'+name+'?'+query},res);return {status:statusCode,body};
}
test('same active attempt reused under simultaneous clicks; buyer and amount snapshots enforced',async()=>{
 const f=await fixture();const attempts=await Promise.all(Array.from({length:4},()=>service.create(req(f.buyer,{orderId:f.id,amount:1}))));
 assert.ok(attempts.every(a=>a.attemptId===f.attempt.attemptId));assert.equal((await db.collection('paymentAttempts').where('orderId','==',f.id).get()).size,1);
 await assert.rejects(service.create(req(f.seller,{orderId:f.id})),e=>e.code==='permission-denied');
 await db.doc('payments/'+f.id).update({amount:1});await assert.rejects(service.create(req(f.buyer,{orderId:f.id})),/số tiền/);
});
test('IPN route rejects wrong signature, TmnCode, duplicate params, missing reference and amount; no writes',async()=>{
 const f=await fixture();const bad=callback(f);bad.vnp_SecureHash='0'.repeat(128);assert.equal((await http('vnpayIpn',bad)).body.RspCode,'97');
 assert.equal((await http('vnpayIpn',callback(f,{vnp_TmnCode:'BADCODE1'}))).body.RspCode,'01');
 assert.equal((await http('vnpayIpn',callback(f,{vnp_TxnRef:'MISSING'}))).body.RspCode,'01');
 assert.equal((await http('vnpayIpn',callback(f,{vnp_Amount:'1'}))).body.RspCode,'04');
 assert.equal((await http('vnpayIpn',callback(f),{raw:'/vnpayIpn?vnp_TxnRef=X&vnp_TxnRef=Y'})).body.RspCode,'97');
 assert.equal((await f.payment()).get('status'),'PENDING');assert.equal((await http('vnpayIpn',callback(f),{method:'POST'})).status,405);
});
test('concurrent valid IPN commits one receipt and notifications, all items SOLD; delayed failure cannot downgrade',async()=>{
 const f=await fixture();const replies=await Promise.all(Array.from({length:4},()=>http('vnpayIpn',callback(f))));
 assert.equal(replies.filter(r=>r.body.RspCode==='00').length,1);assert.equal(replies.filter(r=>r.body.RspCode==='02').length,3);
 assert.equal((await f.order()).get('orderStatus'),'CONFIRMED');assert.equal((await f.payment()).get('status'),'PAID');
 for(const post of f.posts)assert.equal((await db.doc('posts/'+post).get()).get('status'),'SOLD');
 assert.equal((await db.collection('moneyTransactions').where('orderId','==',f.id).get()).size,1);
 assert.equal((await db.collection(`users/${f.buyer}/notifications`).get()).size,1);
 await apply(f,{vnp_ResponseCode:'24',vnp_TransactionStatus:'02',vnp_TransactionNo:'0'});assert.equal((await f.payment()).get('status'),'PAID');
});
test('Return before/after IPN only offers app navigation; never marks payment or exposes callback',async()=>{
 const f=await fixture();const before=await http('vnpayReturn',callback(f));assert.equal(before.status,200);assert.ok(before.body.includes('fashion2hand://payment/'));assert.ok(!before.body.includes('vnp_SecureHash'));
 assert.equal((await f.payment()).get('status'),'PENDING');await apply(f);await http('vnpayReturn',callback(f));assert.equal((await f.payment()).get('status'),'PAID');
});
test('retry has new reference; old attempt succeeds without double paying the order; extra success preserved',async()=>{
 const f=await fixture();await apply(f,{vnp_ResponseCode:'24',vnp_TransactionStatus:'02',vnp_TransactionNo:'0'});
 const second=await service.create(req(f.buyer,{orderId:f.id}));assert.notEqual(second.attemptId,f.attempt.attemptId);
 await apply(f);const newer={...f,attempt:second};await apply(newer,{vnp_TransactionNo:String(transactionBase+1000000+sequence)});
 assert.equal((await f.payment()).get('successfulAttemptId'),f.attempt.attemptId);
 assert.equal((await f.order()).get('paymentReviewReason'),'DUPLICATE_SUCCESS');
 assert.equal((await db.collection('moneyTransactions').where('orderId','==',f.id).get()).size,2);
});
test('success after expiration/reassigned inventory is booked for review without resurrecting or stealing items',async()=>{
 const f=await fixture();await db.doc('orders/'+f.id).update({orderStatus:'CANCELLED',paymentStatus:'EXPIRED',paymentDeadline:new Date(Date.now()-1000)});
 for(const post of f.posts)await db.doc('posts/'+post).update({reservedForOrderId:'OTHER',status:'SOLD'});
 assert.equal((await apply(f)).RspCode,'00');assert.equal((await f.order()).get('orderStatus'),'CANCELLED');assert.equal((await f.order()).get('paymentStatus'),'PAID');
 assert.equal((await db.doc('moneyTransactions/received_'+f.id).get()).get('status'),'REVIEW_REQUIRED');
 for(const post of f.posts)assert.equal((await db.doc('posts/'+post).get()).get('reservedForOrderId'),'OTHER');
});
test('atomic cancel race and IPN: no fake refund or lost receipt',async()=>{
 const f=await fixture();const policy=require('../payment-policy');
 await Promise.all([apply(f),db.runTransaction(async tx=>{
   const order=await tx.get(db.doc('orders/'+f.id)),pay=await tx.get(db.doc('payments/'+f.id));
   const posts=await Promise.all(f.posts.map(id=>tx.get(db.doc('posts/'+id))));
   if(pay.get('status')==='PAID')policy.requestRefund(tx,db,FieldValue,order,pay,'Test cancel');
   else {tx.update(order.ref,{orderStatus:'CANCELLED'});tx.update(pay.ref,{status:'CANCELLED'});}
   for(const post of posts)if(post.get('reservedForOrderId')===f.id)tx.update(post.ref,{status:'ACTIVE',reservedForOrderId:FieldValue.delete()});
 })]);
 assert.equal((await f.order()).get('orderStatus'),'CANCELLED');assert.equal((await f.payment()).get('status'),'PAID');
 const refund=await db.doc('refunds/'+f.id).get();if(refund.exists)assert.equal(refund.get('status'),'REVIEW_REQUIRED');
 assert.ok((await db.doc('moneyTransactions/received_'+f.id).get()).exists);
});
test('legacy single-item schema works; separate sellers retain separate payment states',async()=>{
 const one=await fixture({legacy:true,count:1}),two=await fixture({count:3});await apply(one);
 assert.equal((await one.order()).get('orderStatus'),'CONFIRMED');assert.equal((await two.order()).get('orderStatus'),'PENDING_PAYMENT');
});
test('QueryDR recovers lost IPN, enforces ownership/rate limit and shares receipt idempotency',async()=>{
 const f=await fixture();const fields=callback(f);
 const queryProvider=new VnpayProvider({tmnCode:'TESTONLY',secret:'emulator-test-secret-only',env:{...process.env,VNPAY_QUERY_IP:'203.0.113.5'},fetchImpl:async()=>{
   const d={...fields,vnp_ResponseId:'QUERYRESPONSE',vnp_Command:'querydr',vnp_Message:'Success',vnp_TransactionType:'01',vnp_OrderInfo:'Query',vnp_BankCode:'NCB'};
   d.vnp_SecureHash=hmac('emulator-test-secret-only',pipeData(d,QUERY_RESPONSE_FIELDS));return {ok:true,json:async()=>d};
 }});
 const svc=require('../vnpay-payments')({...ctx,provider:()=>queryProvider});
 await assert.rejects(svc.reconcile(req('OUTSIDER',{orderId:f.id})),e=>e.code==='permission-denied');
 assert.equal((await svc.reconcile(req(f.buyer,{orderId:f.id}))).paymentStatus,'PAID');
 assert.equal((await svc.reconcile(req(f.buyer,{orderId:f.id}))).paymentStatus,'PAID');
 assert.equal((await apply(f)).RspCode,'02');assert.equal((await db.collection('moneyTransactions').where('orderId','==',f.id).get()).size,1);
});

test('QueryDR network errors retain pending payment and emit only safe diagnostics',async()=>{
 const f=await fixture(),logs=[];const p=provider();
 p.query=async()=>{throw new (require('../vnpay-provider').VnpayError)('unavailable','sensitive test transport text',{reason:'QUERY_NETWORK_ERROR',networkCode:'ECONNRESET'});};
 const svc=require('../vnpay-payments')({...ctx,provider:()=>p,logger:{warn:(message,data)=>logs.push({message,data})}});
 await assert.rejects(svc.reconcile(req(f.buyer,{orderId:f.id})),e=>e.code==='unavailable');
 assert.equal((await f.payment()).get('status'),'PENDING');assert.equal((await db.doc('moneyTransactions/received_'+f.id).get()).exists,false);
 assert.equal(logs[0].data.reason,'QUERY_NETWORK_ERROR');assert.equal(logs[0].data.referenceHash.length,12);assert.ok(!JSON.stringify(logs).includes('sensitive test transport text'));
});

test('verified QueryDR after cancellation records late payment for review without reclaiming inventory',async()=>{
 const f=await fixture(),fields=callback(f),p=provider();
 await db.doc('orders/'+f.id).update({orderStatus:'CANCELLED',paymentStatus:'EXPIRED'});
 for(const post of f.posts)await db.doc('posts/'+post).update({status:'ACTIVE',reservedForOrderId:FieldValue.delete()});
 p.query=async()=>p.normalize(fields,{query:true});
 const svc=require('../vnpay-payments')({...ctx,provider:()=>p});
 const result=await svc.reconcile(req(f.buyer,{orderId:f.id}));
 assert.equal(result.paymentStatus,'PAID');assert.equal(result.orderStatus,'CANCELLED');assert.equal(result.needsReview,true);
 for(const post of f.posts)assert.equal((await db.doc('posts/'+post).get()).get('status'),'ACTIVE');
 assert.equal((await db.doc('moneyTransactions/received_'+f.id).get()).get('status'),'REVIEW_REQUIRED');
});

test('pending automatic reconciliation is rate limited and cannot manufacture a receipt',async()=>{
 const f=await fixture();let calls=0;
 const p=provider();p.query=async()=>{calls++;return {pending:true};};
 const svc=require('../vnpay-payments')({...ctx,provider:()=>p});
 assert.equal((await svc.reconcile(req(f.buyer,{orderId:f.id}))).paymentStatus,'PENDING');
 await assert.rejects(svc.reconcile(req(f.buyer,{orderId:f.id})),e=>e.code==='resource-exhausted');
 assert.equal(calls,1);assert.equal((await db.doc('moneyTransactions/received_'+f.id).get()).exists,false);
});

test('provider throttle persists five-minute backoff without accepting unsigned payment evidence',async()=>{
 const f=await fixture();let calls=0,clock=Date.now();
 const p=provider();p.query=async()=>{calls++;if(calls===1)throw new (require('../vnpay-provider').VnpayError)('resource-exhausted','Rate limited',{reason:'QUERY_RATE_LIMITED',retryAfterSeconds:300});return {pending:true};};
 const svc=require('../vnpay-payments')({...ctx,provider:()=>p,now:()=>clock});
 await assert.rejects(svc.reconcile(req(f.buyer,{orderId:f.id})),e=>e.code==='resource-exhausted');
 assert.equal((await f.order()).get('paymentQueryRetryAfter').toMillis(),clock+300000);
 clock+=61000;
 await assert.rejects(svc.reconcile(req(f.buyer,{orderId:f.id})),e=>e.code==='resource-exhausted');
 assert.equal(calls,1);
 clock+=240001;
 assert.equal((await svc.reconcile(req(f.buyer,{orderId:f.id}))).paymentStatus,'PENDING');
 assert.equal(calls,2);assert.equal((await db.doc('moneyTransactions/received_'+f.id).get()).exists,false);
});
test('verified prepaid VNPAY can continue GHN staging with zero COD; production stays blocked',async()=>{
 const f=await fixture();await apply(f);const shipping=require('../ghn-shipments')({...ctx,provider:()=>({settings:{environment:'staging'}})});
 const order=(await f.order()).data(),payload=shipping.payload(order,'TEST');assert.equal(payload.cod_amount,0);assert.equal(payload.payment_type_id,1);
 assert.throws(()=>shipping.payload({...order,shippingQuote:{...order.shippingQuote,environment:'production'}},'TEST'));
 assert.throws(()=>shipping.payload({...order,paymentNeedsReview:true},'TEST'));
});
test('sandbox settlement completes delivery but records manual payout review, never RELEASED or transferred',async()=>{
 const f=await fixture();await apply(f);await db.doc('orders/'+f.id).update({orderStatus:'DELIVERED'});
 await db.doc('shipments/'+f.id).set({status:'DELIVERED',events:[{status:'DELIVERED'}]});
 const settle=require('../escrow')(ctx);await settle(f.id,f.buyer);
 assert.equal((await f.order()).get('orderStatus'),'COMPLETED');assert.equal((await db.doc('moneyTransactions/payout_'+f.id).get()).get('status'),'REVIEW_REQUIRED');
 assert.equal((await db.doc('moneyTransactions/received_'+f.id).get()).get('status'),'HOLDING');
});
test('public IPN is secret-bound; commit failure returns 99, never success; status remains authenticated',async()=>{
 assert.equal(endpoints.vnpayIpn.options.invoker,'public');assert.equal(endpoints.vnpayIpn.options.secrets.length,2);
 await assert.rejects(service.status({data:{orderId:'ANY'}}),e=>e.code==='unauthenticated');
 const f=await fixture();const broken={...ctx,db:{...db,doc:db.doc.bind(db),runTransaction:async()=>{throw Error('raw secret transport error');}},exports:{...endpoints}};
 require('../vnpay-integration')({...broken,onCall,onRequest,defineSecret:name=>({value:()=>process.env[name]})});
 const previous=endpoints.vnpayIpn;endpoints.vnpayIpn=broken.exports.vnpayIpn;
 try{const response=await http('vnpayIpn',callback(f));assert.equal(response.body.RspCode,'99');assert.ok(!JSON.stringify(response).includes('raw secret'));}finally{endpoints.vnpayIpn=previous;}
});
test('real cancel/reject/expiry handlers preserve VNPAY receipt and request review rather than fake refund',async()=>{
 const api=require('../index');
 for(const action of ['cancelOrder','rejectOrder']){
   const f=await fixture();await apply(f);
   await api[action].run(req(action==='cancelOrder'?f.buyer:f.seller,{orderId:f.id,reason:'Test reject'}));
   assert.equal((await f.order()).get('orderStatus'),'CANCELLED');assert.equal((await f.payment()).get('status'),'PAID');
   assert.equal((await db.doc('refunds/'+f.id).get()).get('status'),'REVIEW_REQUIRED');
 }
 const expired=await fixture();await apply(expired);await db.doc('orders/'+expired.id).update({sellerResponseDeadline:new Date(Date.now()-10000)});
 await api.refreshOrder.run(req(expired.buyer,{orderId:expired.id}));assert.equal((await expired.payment()).get('status'),'PAID');assert.equal((await expired.order()).get('refundStatus'),'REVIEW_REQUIRED');
 const scheduled=await fixture();await apply(scheduled);await db.doc('orders/'+scheduled.id).update({sellerResponseDeadline:new Date(Date.now()-10000)});
 await api.expireUnpaidAndUnconfirmedOrders.run({});assert.equal((await scheduled.order()).get('orderStatus'),'CANCELLED');assert.equal((await scheduled.payment()).get('status'),'PAID');
});
test('mock payment callable and old authorized webhook cannot fake VNPAY payment; HTTP IPN needs no bearer',async()=>{
 const api=require('../index'),f=await fixture();process.env.ENABLE_SIMULATED_PAYMENTS='true';
 await assert.rejects(api.simulatePaymentResult.run(req(f.buyer,{orderId:f.id,status:'PAID'})),e=>e.code==='failed-precondition');
 process.env.PAYMENT_WEBHOOK_TOKEN='test-webhook-only';let responseCode=200;
 const res={status:code=>{responseCode=code;return res;},send:()=>res,json:()=>res};
 await api.updatePaymentFromProvider({method:'POST',get:()=> 'Bearer test-webhook-only',body:{eventId:'fake-'+f.id,orderId:f.id,providerReference:'FAKE',status:'PAID'}},res);
 assert.notEqual(responseCode,200);assert.equal((await f.payment()).get('status'),'PENDING');
 assert.equal((await http('vnpayIpn',callback(f))).body.RspCode,'00');
});
test('HTTP IPN listener on localhost accepts GET, returns protocol JSON only after receipt commit',async()=>{
 const f=await fixture(),server=require('node:http').createServer((request,response)=>{
   const adapter={set:(key,value)=>{response.setHeader(key,value);return adapter;},status:code=>{response.statusCode=code;return adapter;},json:body=>{response.setHeader('Content-Type','application/json');response.end(JSON.stringify(body));return adapter;}};
   endpoints.vnpayIpn({method:request.method,url:request.url},adapter).catch(()=>{response.statusCode=500;response.end();});
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{const fields=callback(f),url=`http://127.0.0.1:${server.address().port}/vnpayIpn?${canonical(fields)}&vnp_SecureHash=${fields.vnp_SecureHash}`;const response=await fetch(url);assert.equal(response.status,200);assert.equal((await response.json()).RspCode,'00');assert.equal((await f.payment()).get('status'),'PAID');}
 finally{await new Promise(resolve=>server.close(resolve));}
});
test('reversed/suspicious financial result is recorded for review; no extra attempt or shipment',async()=>{
 const f=await fixture();await apply(f,{vnp_ResponseCode:'07',vnp_TransactionStatus:'07'});
 assert.equal((await f.order()).get('paymentNeedsReview'),true);assert.equal((await f.payment()).get('status'),'PENDING');
 await assert.rejects(service.create(req(f.buyer,{orderId:f.id})),/đối soát/);
});
test('multi-item total corruption and attempt flood blocked; pending timeout is not FAILED',async()=>{
 const f=await fixture();await db.doc('orders/'+f.id).update({items:[{postId:f.posts[0],price:1},{postId:f.posts[1],price:2}]});
 await assert.rejects(service.create(req(f.buyer,{orderId:f.id})),/Snapshot/);
 const g=await fixture();await apply(g,{vnp_ResponseCode:'00',vnp_TransactionStatus:'01',vnp_TransactionNo:'0'});
 assert.equal((await g.payment()).get('status'),'PENDING');assert.equal((await service.create(req(g.buyer,{orderId:g.id}))).attemptId,g.attempt.attemptId);
 await apply(g,{vnp_ResponseCode:'24',vnp_TransactionStatus:'02',vnp_TransactionNo:'0'});await db.doc('payments/'+g.id).update({attemptCount:10});
 await assert.rejects(service.create(req(g.buyer,{orderId:g.id})),e=>e.code==='resource-exhausted');
});
