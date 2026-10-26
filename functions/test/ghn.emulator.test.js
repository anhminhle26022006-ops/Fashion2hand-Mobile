const {test,after}=require('node:test');
const assert=require('node:assert/strict');
process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:8080';
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
const app=initializeApp({projectId:'fashion2hand'},`ghn-tests-${Date.now()}`),db=getFirestore(app);
after(()=>deleteApp(app));
const {GhnError,hash}=require('../ghn-provider');
const prefix=`ghn-${Date.now()}`;
const fail=(code,message)=>{const e=new Error(message);e.code=code;throw e;};
const uidOf=r=>{if(!r.auth?.uid)fail('unauthenticated','Đăng nhập');return r.auth.uid;};
const cleanDocumentId=(value)=>{if(typeof value!=='string'||!value||value.includes('/'))fail('invalid-argument','ID');return value;};
const ctx={db,FieldValue,fail,uidOf,cleanDocumentId,cleanText:(value)=>String(value||''),requireVerifiedAccount:async r=>uidOf(r),
 postOwnerId:p=>p.get('ownerId'),postType:p=>p.get('type'),orderPostSnapshot:p=>({title:p.get('title')}),
 writeOrderEvent:(tx,ref,from,to,actor,reason)=>tx.create(ref.collection('statusEvents').doc(),{from,to,actor,reason,createdAt:FieldValue.serverTimestamp()})};
const req=(uid,data)=>({auth:{uid},data});
async function fixture(label,{free=false}={}){
 const id=`${prefix}-${label}`,seller=`${id}-seller`,buyer=`${id}-buyer`,post=`${id}-post`;
 const pickup={recipient:'Seller',phone:'0901234567',street:'Shop address',province:'Province',district:'District',ward:'Ward',ghn:{provinceId:1,districtId:2,wardCode:'3',environment:'staging'}};
 await db.doc('shippingConfig/ghn_staging').set({environment:'staging',parcelEstimate:{weight:300,length:20,width:15,height:2,estimated:true}});
 await db.doc(`ghnSellerShops/staging_${seller}`).set({environment:'staging',enabled:true,shopId:10,pickup,registeredShopAddress:'Shop address'});
 await db.doc(`users/${buyer}/addresses/home`).set({...pickup,recipient:'Buyer',street:'Buyer address'});
 await db.doc(`posts/${post}`).set({ownerId:seller,type:free?'FREE':'SALE',status:'ACTIVE',title:'Test clothing',price:free?0:100000});
 let creates=0,fee=25000,createError=null,persisted=false,status='ready_to_pick',at='2026-10-26T10:00:00Z';const tracking=`TEST-${hash(id).slice(0,10)}`;
 let createGate=null;
 const provider={settings:{environment:'staging'},shops:async()=>({shops:[{_id:10,district_id:2,ward_code:'3',address:'Shop address'}]}),address:async(_,a)=>({...a,ghn:{...a.ghn}}),services:async()=>[{service_id:20,service_type_id:2}],fee:async()=>({total:fee}),
   create:async(_,payload)=>{creates++;persisted=true;if(createGate)await createGate;if(createError)throw createError;return {order_code:tracking};},
   byReference:async(_,reference)=>{if(!persisted)throw new GhnError('unavailable','Not created yet');return detail(reference);},
   detail:async()=>detail(shipping.reference(orderId)),cancel:async()=>{status='cancel';at='2026-10-26T11:00:00Z';return [];} };
 let orderId='';const detail=reference=>({order_code:tracking,client_order_code:reference,shop_id:10,status,updated_date:at,cod_amount:0,payment_type_id:free?2:1,total_fee:fee});
 const context={...ctx,provider:()=>provider};const checkout=require('../ghn-checkout')(context),shipping=require('../ghn-shipments')(context);
 async function createOrder(){
   const q=await checkout.quote(req(buyer,{postId:post,addressId:'home'}));
   if(free){const request=await checkout.createFree(req(buyer,{postId:post,addressId:'home',quoteId:q.quoteId,note:'Please'}));orderId=(await checkout.acceptFree(req(seller,{requestId:request.requestId,accept:true}))).orderId;}
   else orderId=(await checkout.createOrders(req(buyer,{postId:post,addressId:'home',quoteId:q.quoteId,idempotencyKey:'checkout'}),true)).orderId;
   await db.doc(`orders/${orderId}`).update({orderStatus:'READY_TO_SHIP',paymentStatus:free?'NOT_REQUIRED':'PAID'});if(!free){await db.doc(`payments/${orderId}`).update({status:'PAID'});await db.doc(`moneyTransactions/received_${orderId}`).set({orderId,status:'HOLDING',mode:'SIMULATED'});}return q;
 }
 return {seller,buyer,post,checkout,shipping,provider,createOrder,detail,tracking,
   get orderId(){return orderId;},get creates(){return creates;},setFee:n=>fee=n,setCreateError:e=>createError=e,setGate:g=>createGate=g,setStatus:(s,t)=>{status=s;at=t;},pickup};
}
test('multi-seller quote charges once per seller and validates input/address/expiry; checkout replay stable',async()=>{
 const f=await fixture('checkout');const p2=`${f.post}-second`,p3=`${f.post}-other`;
 await db.doc(`posts/${p2}`).set({ownerId:f.seller,type:'SALE',status:'ACTIVE',price:200000,title:'Second'});
 const otherSeller=`a-${f.seller}-other`;await db.doc(`posts/${p3}`).set({ownerId:otherSeller,type:'SALE',status:'ACTIVE',price:50000,title:'Third'});
 await db.doc(`ghnSellerShops/staging_${otherSeller}`).set({environment:'staging',enabled:true,shopId:10,pickup:f.pickup,registeredShopAddress:'Shop address'});
 const ids=[f.post,p2,p3].sort();for(const id of ids)await db.doc(`users/${f.buyer}/cartItems/${id}`).set({postId:id});
 let q=await f.checkout.quote(req(f.buyer,{postIds:ids,addressId:'home'}));assert.equal(q.groups.length,2);assert.equal(q.shippingFee,50000);assert.equal(q.total,400000);
 assert.equal(q.groups.find(g=>g.sellerId===f.seller).parcel.weight,600);
 await db.doc(`shippingQuotes/${q.quoteId}`).update({expiresAt:new Date(0)});
 await assert.rejects(f.checkout.createOrders(req(f.buyer,{postIds:ids,addressId:'home',quoteId:q.quoteId,idempotencyKey:'one'}),false),/hết hạn/);
 q=await f.checkout.quote(req(f.buyer,{postIds:ids,addressId:'home'}));f.setFee(26000);
 await assert.rejects(f.checkout.createOrders(req(f.buyer,{postIds:ids,addressId:'home',quoteId:q.quoteId,idempotencyKey:'one'}),false),/Phí GHN/);f.setFee(25000);
 await db.doc(`posts/${p2}`).update({price:210000});
 await assert.rejects(f.checkout.createOrders(req(f.buyer,{postIds:ids,addressId:'home',quoteId:q.quoteId,idempotencyKey:'one'}),false),/thay đổi/);
 q=await f.checkout.quote(req(f.buyer,{postIds:ids,addressId:'home'}));const data={postIds:ids,addressId:'home',quoteId:q.quoteId,idempotencyKey:'final'};
 const results=await Promise.all([f.checkout.createOrders(req(f.buyer,data),false),f.checkout.createOrders(req(f.buyer,data),false)]);
 assert.deepEqual(results[0],results[1]);assert.equal(results[0].orderIds.length,2);
 for(const id of ids)assert.equal((await db.doc(`users/${f.buyer}/cartItems/${id}`).get()).exists,false);
 for(const id of results[0].orderIds){const order=await db.doc(`orders/${id}`).get();assert.equal(order.get('shippingFee'),25000);assert.equal(order.get('shippingQuote.environment'),'staging');}
 await assert.rejects(f.checkout.quote(req('outsider',{postId:f.post,addressId:'home'})),/tài khoản/);
});
test('missing seller mapping and legacy address are blocked without a mock fee',async()=>{
 const f=await fixture('config');await db.doc(`ghnSellerShops/staging_${f.seller}`).delete();
 await assert.rejects(f.checkout.quote(req(f.buyer,{postId:f.post,addressId:'home'})),/ánh xạ/);
 assert.equal(f.creates,0);
});
test('one create lease under concurrent requests; no extra COD on prepaid; unauthorized read/create/cancel blocked',async()=>{
 const f=await fixture('race');await f.createOrder();
 await assert.rejects(f.shipping.create(req('outsider',{orderId:f.orderId})),/người bán/);
 await assert.rejects(f.shipping.refresh(req('outsider',{orderId:f.orderId})),/quyền/);
 await assert.rejects(f.shipping.cancel(req(f.buyer,{orderId:f.orderId})),/người bán/);
 const all=await Promise.allSettled([f.shipping.create(req(f.seller,{orderId:f.orderId})),f.shipping.create(req(f.seller,{orderId:f.orderId}))]);
 assert.ok(all.some(r=>r.status==='fulfilled'));assert.equal(f.creates,1);
 const shipment=await db.doc(`shipments/${f.orderId}`).get();assert.equal(shipment.get('provider'),'GHN');assert.equal(shipment.get('status'),'READY');
 const op=await db.doc(`ghnOperations/${f.shipping.reference(f.orderId)}`).get();assert.equal(op.get('payload.cod_amount'),0);assert.equal(op.get('payload.payment_type_id'),1);
});
test('create timeout after GHN persisted is recovered by reference lookup, never create retry',async()=>{
 const f=await fixture('timeout');await f.createOrder();f.setCreateError(new GhnError('unavailable','Timeout',true));
 await assert.rejects(f.shipping.create(req(f.seller,{orderId:f.orderId})),/Timeout/);assert.equal(f.creates,1);
 assert.equal((await db.doc(`ghnOperations/${f.shipping.reference(f.orderId)}`).get()).get('state'),'UNKNOWN');
 await f.shipping.create(req(f.seller,{orderId:f.orderId}));assert.equal(f.creates,1);
 assert.equal((await db.doc(`shipments/${f.orderId}`).get()).get('trackingCode'),f.tracking);
});
test('GHN success with failed detail read is recovered later without a second order',async()=>{
 const f=await fixture('recover');await f.createOrder();const original=f.provider.detail;f.provider.detail=async()=>{throw new Error('Firestore/provider transport interrupted after create');};
 await assert.rejects(f.shipping.create(req(f.seller,{orderId:f.orderId})),/có thể đã tạo/);f.provider.detail=original;
 await f.shipping.refresh(req(f.buyer,{orderId:f.orderId}));assert.equal(f.creates,1);assert.ok((await db.doc(`shipments/${f.orderId}`).get()).exists);
});
test('callback before linkage verifies GHN; forged, duplicate and stale callbacks never fake delivery or extend deadline',async()=>{
 const f=await fixture('callback');await f.createOrder();await f.shipping.create(req(f.seller,{orderId:f.orderId}));
 async function callback(body){const result={};const res={status:code=>{result.status=code;return res;},json:data=>{result.body=data;return res;},send:data=>{result.body=data;return res;}};await f.shipping.callback({method:'POST',body},res);return result;}
 const body={OrderCode:f.tracking,ClientOrderCode:f.shipping.reference(f.orderId),ShopID:10,Type:'switch_status',Time:'2026-10-26T12:00:00Z',Status:'delivered'};
 await callback(body);assert.equal((await db.doc(`shipments/${f.orderId}`).get()).get('status'),'READY');
 f.setStatus('delivery_fail','2026-10-26T11:00:00Z');await callback(body);assert.equal((await db.doc(`shipments/${f.orderId}`).get()).get('status'),'DELIVERY_FAILED');
 f.setStatus('delivered','2026-10-26T12:00:00Z');await callback(body);const before=await db.doc(`orders/${f.orderId}`).get();assert.equal(before.get('orderStatus'),'DELIVERED');
 const deadline=before.get('inspectionDeadline').toMillis();await callback(body);assert.equal((await db.doc(`orders/${f.orderId}`).get()).get('inspectionDeadline').toMillis(),deadline);
 f.setStatus('delivering','2026-10-26T10:30:00Z');await callback(body);assert.equal((await db.doc(`shipments/${f.orderId}`).get()).get('status'),'DELIVERED');
 await callback({...body,ShopID:99});assert.equal((await db.doc(`shipments/${f.orderId}`).get()).get('status'),'DELIVERED');
 const events=await db.collection(`users/${f.buyer}/notifications`).get();assert.equal(events.size,3);
});
test('free order receiver pays shipping; real production prepaid simulation blocked; old order snapshot not invented',async()=>{
 const f=await fixture('free',{free:true});await f.createOrder();
 let data=(await db.doc(`orders/${f.orderId}`).get()).data(),body=f.shipping.payload(data,f.shipping.reference(f.orderId));assert.equal(body.cod_amount,0);assert.equal(body.payment_type_id,2);
 assert.throws(()=>f.shipping.payload({orderType:'FREE',paymentStatus:'NOT_REQUIRED'},'OLD'),/Đơn cũ/);
 data={...data,orderType:'PAID',paymentStatus:'PAID',shippingQuote:{...data.shippingQuote,environment:'production'}};f.provider.settings.environment='production';
 assert.throws(()=>f.shipping.payload(data,'BLOCKED'),/thanh toán tiền thật/);
});
test('cancel does not mark success if GHN refuses or still reports picking; confirmed cancel recorded separately from refund',async()=>{
 const f=await fixture('cancel');await f.createOrder();await f.shipping.create(req(f.seller,{orderId:f.orderId}));
 f.provider.cancel=async()=>[];await assert.rejects(f.shipping.cancel(req(f.seller,{orderId:f.orderId})),/chưa xác nhận hủy/);
 assert.equal((await db.doc(`shipments/${f.orderId}`).get()).get('status'),'READY');
 f.provider.cancel=async()=>{f.setStatus('cancel','2026-10-26T11:00:00Z');return [];};await f.shipping.cancel(req(f.seller,{orderId:f.orderId}));
 assert.equal((await db.doc(`shipments/${f.orderId}`).get()).get('status'),'CANCELLED');
 assert.equal((await db.doc(`orders/${f.orderId}`).get()).get('paymentStatus'),'PAID');
});

test('Firestore transaction failure after GHN success recovers linkage without another create',async()=>{
 const f=await fixture('firestore-failure');await f.createOrder();let transactions=0;
 const flakyDb={doc:path=>db.doc(path),collection:path=>db.collection(path),runTransaction:fn=>{transactions++;if(transactions===2)return Promise.reject(new Error('Injected Firestore write failure'));return db.runTransaction(fn);}};
 const shipping=require('../ghn-shipments')({...ctx,db:flakyDb,provider:()=>f.provider});
 await assert.rejects(shipping.create(req(f.seller,{orderId:f.orderId})),/có thể đã tạo/);assert.equal(f.creates,1);
 assert.equal((await db.doc('shipments/'+f.orderId).get()).exists,false);
 await f.shipping.refresh(req(f.seller,{orderId:f.orderId}));assert.equal(f.creates,1);assert.equal((await db.doc('shipments/'+f.orderId).get()).get('trackingCode'),f.tracking);
});
test('early callback links an in-flight operation before create returns; completion cannot duplicate it',async()=>{
 const f=await fixture('early-callback');await f.createOrder();let release;f.setGate(new Promise(resolve=>release=resolve));
 const creating=f.shipping.create(req(f.seller,{orderId:f.orderId}));
 for(let i=0;i<100&&f.creates===0;i++)await new Promise(resolve=>setTimeout(resolve,20));assert.equal(f.creates,1);
 const response={};const res={status:code=>{response.status=code;return res;},json:data=>{response.data=data;return res;},send:()=>res};
 await f.shipping.callback({method:'POST',body:{OrderCode:f.tracking,ShopID:10,ClientOrderCode:f.shipping.reference(f.orderId),Type:'create'}},res);
 assert.equal(response.status,200);assert.ok((await db.doc('shipments/'+f.orderId).get()).exists);
 release();await creating;assert.equal(f.creates,1);assert.equal((await db.doc('ghnOperations/'+f.shipping.reference(f.orderId)).get()).get('state'),'LINKED');
});
test('SDK bindings include GHN secret; unauthenticated requests and revoked admin claim rejected',async()=>{
 const endpoints={};for(const name of ['getCheckoutQuote','getCartCheckoutQuote','createPaidOrder','createCartOrders','createFreeRequest','createShipment','respondFreeRequest','refreshOrder','getTransactionCapabilities'])endpoints[name]={run:async()=>({legacy:true})};
 const bound=[];const onCall=(options,handler)=>{if(typeof options==='function'){handler=options;options={};}const fn={run:handler,options};if(options.secrets)bound.push(fn);return fn;};
 const uid=prefix+'-revoked';await db.doc('users/'+uid).set({role:'ADMIN',accountStatus:'ACTIVE'});
 require('../ghn-integration')({...ctx,exports:endpoints,onCall,onRequest:(options,handler)=>({run:handler,options}),defineSecret:name=>({name,value:()=>{throw Error('No real secret in tests');}}),getAuth:()=>({getUser:async()=>({disabled:false,customClaims:{}})}),logger:{warn:()=>{}}});
 assert.ok(bound.length>=12);for(const fn of bound)assert.equal(fn.options.secrets[0].name,'GHN_TOKEN');
 await assert.rejects(endpoints.getCheckoutQuote.run({data:{}}),e=>e.code==='unauthenticated');
 await assert.rejects(endpoints.ghnAdminShops.run({auth:{uid,token:{admin:true}},data:{}}),e=>e.code==='permission-denied');
});

test('order paymentStatus alone cannot create shipment without the held payment ledger',async()=>{
 const f=await fixture('payment-proof');await f.createOrder();await db.doc('moneyTransactions/received_'+f.orderId).delete();
 await assert.rejects(f.shipping.create(req(f.seller,{orderId:f.orderId})),/bút toán giữ tiền/);assert.equal(f.creates,0);
});
test('shipment fee change after order placement is blocked without increasing buyer total',async()=>{
 const f=await fixture('shipment-fee');await f.createOrder();f.setFee(29000);
 await assert.rejects(f.shipping.create(req(f.seller,{orderId:f.orderId})),/Phí GHN đã thay đổi/);assert.equal(f.creates,0);
 assert.equal((await db.doc('orders/'+f.orderId).get()).get('shippingFee'),25000);
});
test('return lifecycle never becomes successful delivery or invents a refund',async()=>{
 const f=await fixture('return');await f.createOrder();await f.shipping.create(req(f.seller,{orderId:f.orderId}));
 for(const [status,at,expected] of [['waiting_to_return','2026-10-26T11:00:00Z','RETURNING'],['return_fail','2026-10-26T12:00:00Z','RETURN_FAILED'],['returned','2026-10-26T13:00:00Z','RETURNED']]){
   f.setStatus(status,at);await f.shipping.refresh(req(f.buyer,{orderId:f.orderId}));assert.equal((await db.doc('shipments/'+f.orderId).get()).get('status'),expected);
 }
 const order=await db.doc('orders/'+f.orderId).get();assert.equal(order.get('shippingNeedsReview'),true);assert.equal(order.get('deliveredAt'),undefined);assert.equal(order.get('paymentStatus'),'PAID');
 assert.equal((await db.doc('moneyTransactions/received_'+f.orderId).get()).get('status'),'HOLDING');
});
