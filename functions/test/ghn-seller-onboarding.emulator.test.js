'use strict';
const {test,after}=require('node:test'),assert=require('node:assert/strict');
process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:8080';
const {initializeApp,deleteApp}=require('firebase-admin/app'),{getFirestore,FieldValue}=require('firebase-admin/firestore');
const app=initializeApp({projectId:'demo-fashion2hand-pickup'},'pickup-tests'),db=getFirestore(app);
after(()=>deleteApp(app));
const {GhnError}=require('../ghn-provider');
const fail=(code,message)=>{throw Object.assign(new Error(message),{code});};
let sequence=0,shopSequence=1000;const prefix='pickup'+Date.now();
async function fixture(){
 const uid=prefix+(++sequence),addressId='home';let clock=Date.now(),calls=0,mode='',gate=null,onCreate=null;
 const source={recipient:'Seller',phone:'0901234567',street:'22 Test Road',ghn:{provinceId:1,districtId:2,wardCode:'3',environment:'staging'}};
 await db.doc(`users/${uid}`).set({accountStatus:'ACTIVE'});await db.doc(`users/${uid}/addresses/${addressId}`).set(source);
 const list=[{_id:10,name:'Platform',phone:'0909999999',district_id:9,ward_code:'8',address:'Platform address'}];
 const add=body=>{const shop={_id:++shopSequence,...body};list.push(shop);return shop;};let lastBody;
 const p={settings:{environment:'staging'},address:async(_,a)=>({...a,ghn:{...a.ghn}}),shops:async()=>({shops:list.slice()}),registerShop:async(base,body)=>{
  assert.equal(base,10);calls++;lastBody=body;if(onCreate)onCreate();if(gate)await gate;
  if(mode==='unknown')throw new GhnError('unavailable','safe',true);
  if(mode==='reject')throw new GhnError('unavailable','safe',false);
  const shop=add(body);return {shop_id:shop._id};
 }};
 const context={db,FieldValue,fail,uidOf:r=>{if(!r.auth?.uid)fail('unauthenticated','Sign in');return r.auth.uid;},cleanDocumentId:id=>{if(typeof id!=='string'||!id||id.includes('/'))fail('invalid-argument','ID');return id;},requireVerifiedAccount:async()=>{},provider:()=>p,bootstrapShopId:()=>10,now:()=>clock};
 const setup=require('../ghn-seller-onboarding')(context);
 return {uid,source,p,list,context,setup,recover:()=>require('../ghn-seller-onboarding').recover(context,uid),request:{auth:{uid},data:{addressId,confirmPickup:true}},mapping:()=>db.doc(`ghnSellerShops/staging_${uid}`).get(),operation:()=>db.doc(`ghnSellerRegistrations/staging_${uid}`).get(),get calls(){return calls;},setMode:v=>mode=v,setGate:(promise,entered)=>{gate=promise;onCreate=entered;},addRemote:()=>add(lastBody),advance:ms=>clock+=ms};
}
test('seller registers own pickup, repeats reuse it, other seller gets separate shop',async()=>{
 const f=await fixture();assert.equal((await f.setup(f.request)).ok,true);assert.equal(f.calls,1);
 assert.equal((await f.mapping()).get('pickup').street,f.source.street);assert.equal((await f.mapping()).get('enabled'),true);
 assert.equal((await f.setup(f.request)).ok,true);assert.equal(f.calls,1);
 const g=await fixture();g.list.push(...f.list.slice(1));await g.setup(g.request);assert.equal(g.calls,1);
 assert.notEqual((await f.operation()).get('name'),(await g.operation()).get('name'));
 assert.notEqual((await f.mapping()).get('shopId'),(await g.mapping()).get('shopId'));
});
test('ownership, authentication, confirmation, active account and staging are enforced',async()=>{
 const f=await fixture();
 await assert.rejects(f.setup({data:f.request.data}),e=>e.code==='unauthenticated');
 await assert.rejects(f.setup({...f.request,data:{addressId:'other',confirmPickup:true}}),e=>e.code==='not-found');
 await assert.rejects(f.setup({...f.request,data:{addressId:'home'}}),e=>e.code==='invalid-argument');
 await db.doc(`users/${f.uid}`).update({accountStatus:'LOCKED'});
 await assert.rejects(f.setup(f.request),e=>e.code==='permission-denied');
 f.p.settings.environment='production';await assert.rejects(f.setup(f.request),e=>e.code==='failed-precondition');assert.equal(f.calls,0);
});
test('existing approved pickup is reused without creating another platform shop',async()=>{
 const f=await fixture();f.list.push({_id:55,name:'Existing',phone:'0901234567',address:'22 Test Road',district_id:2,ward_code:'3'});
 await db.doc(`ghnSellerShops/staging_${f.uid}`).set({environment:'staging',shopId:55,enabled:true});
 await f.setup(f.request);assert.equal(f.calls,0);assert.equal((await f.mapping()).get('shopId'),55);
});
test('concurrent setup only sends one registration',async()=>{
 const f=await fixture();let release,entered;const pending=new Promise(r=>release=r),started=new Promise(r=>entered=r);f.setGate(pending,entered);
 const first=f.setup(f.request);await started;
 await assert.rejects(f.setup(f.request),e=>e.code==='aborted');release();await first;assert.equal(f.calls,1);
});
test('lost response is never retried blindly; matching remote shop repairs mapping',async()=>{
 const f=await fixture();f.setMode('unknown');await assert.rejects(f.setup(f.request));assert.equal((await f.operation()).get('state'),'UNKNOWN');
 await assert.rejects(f.setup(f.request),e=>e.code==='unavailable');assert.equal(f.calls,1);
 f.addRemote();assert.equal((await f.setup(f.request)).ok,true);assert.equal(f.calls,1);assert.equal((await f.operation()).get('state'),'LINKED');
});
test('changed address creates separate pickup without rewriting any order snapshot',async()=>{
 const f=await fixture();await f.setup(f.request);const oldId=(await f.mapping()).get('shopId');f.advance(61000);
 await db.doc(`users/${f.uid}/addresses/home`).update({street:'99 Other Road'});
 await f.setup(f.request);assert.equal(f.calls,2);assert.notEqual((await f.mapping()).get('shopId'),oldId);assert.equal(f.list.find(s=>s._id===oldId).address,'22 Test Road');
});
test('definitive rejection permits controlled retry but enforces cooldown',async()=>{
 const f=await fixture();f.setMode('reject');await assert.rejects(f.setup(f.request));assert.equal((await f.operation()).get('state'),'REJECTED');
 await assert.rejects(f.setup(f.request),e=>e.code==='resource-exhausted');assert.equal(f.calls,1);
 f.advance(61000);f.setMode('');await f.setup(f.request);assert.equal(f.calls,2);
});
test('editing address during registration leaves no active mapping and preserves recovery state',async()=>{
 const f=await fixture();let release,entered;const pending=new Promise(r=>release=r),started=new Promise(r=>entered=r);f.setGate(pending,entered);
 const first=f.setup(f.request);await started;await db.doc(`users/${f.uid}/addresses/home`).update({street:'Changed during call'});release();
 await assert.rejects(first,e=>e.code==='failed-precondition');assert.equal((await f.mapping()).exists,false);assert.equal((await f.operation()).get('state'),'UNKNOWN');
});

test('successful registration links immediately even when the list is unavailable afterwards',async()=>{
 const f=await fixture();let reads=0;const list=f.p.shops;f.p.shops=async()=>{if(++reads>1)throw new GhnError('unavailable','Read temporarily failed');return list();};
 await f.setup(f.request);assert.equal(f.calls,1);assert.equal((await f.operation()).get('state'),'LINKED');assert.equal(reads,1);
 assert.equal((await f.mapping()).get('enabled'),true);
});

test('daily registration cap is enforced before sending a new shop request',async()=>{
 const f=await fixture();await db.doc(`ghnSellerRegistrations/staging_${f.uid}`).set({state:'LINKED',day:new Date().toISOString().slice(0,10),dailyCount:5});
 await assert.rejects(f.setup(f.request),e=>e.code==='resource-exhausted');assert.equal(f.calls,0);
});

test('quote recovery links a pending registered shop without another registration',async()=>{
 const f=await fixture();f.setMode('unknown');await assert.rejects(f.setup(f.request));f.addRemote();
 assert.equal(await f.recover(),true);assert.equal(f.calls,1);assert.equal((await f.mapping()).get('enabled'),true);assert.equal((await f.operation()).get('state'),'LINKED');
});

test('quote recovery cannot link a changed pickup or invent a registration for a new seller',async()=>{
 const f=await fixture();assert.equal(await f.recover(),false);assert.equal(f.calls,0);
 f.setMode('unknown');await assert.rejects(f.setup(f.request));f.addRemote();
 await db.doc(`users/${f.uid}/addresses/home`).update({street:'Different pickup'});
 assert.equal(await f.recover(),false);assert.equal((await f.mapping()).exists,false);assert.equal(f.calls,1);
});

test('real staging reproduction: create succeeds while shop list stays stale, linkage must still succeed',async()=>{
 const f=await fixture(),create=f.p.registerShop,list=f.p.shops;let reads=0;
 f.p.registerShop=async(...args)=>({shop_id:String((await create(...args)).shop_id)});
 f.p.shops=async()=>{const result=await list();reads++;return {shops:result.shops.slice(0,1)};};
 await f.setup(f.request);assert.equal(f.calls,1);assert.equal((await f.mapping()).get('enabled'),true);assert.equal(reads,1);
 assert.equal((await f.operation()).get('creationConfirmed'),true);
 const matches=require('../ghn-seller-onboarding').creationReceiptMatches,mapping=(await f.mapping()).data(),op=(await f.operation()).data();
 assert.equal(matches(mapping,op,f.uid),true);
 assert.equal(matches({...mapping,shopId:999},op,f.uid),false);
 assert.equal(matches(mapping,op,'another-seller'),false);
 assert.equal(matches(mapping,{...op,creationConfirmed:false},f.uid),false);
 assert.equal(matches({...mapping,pickup:{...mapping.pickup,street:'Different pickup'}},op,f.uid),false);
});

test('persisted create response repairs lost linkage even while the shop list remains stale',async()=>{
 const f=await fixture();await f.setup(f.request);await db.doc(`ghnSellerShops/staging_${f.uid}`).delete();
 await db.doc(`ghnSellerRegistrations/staging_${f.uid}`).update({state:'UNKNOWN'});f.p.shops=async()=>({shops:f.list.slice(0,1)});
 assert.equal(await f.recover(),true);assert.equal(f.calls,1);assert.equal((await f.mapping()).get('enabled'),true);
});

test('buyer can get quote during list lag only with an exact server-recorded create receipt',async()=>{
 const f=await fixture();await f.setup(f.request);const shopId=(await f.mapping()).get('shopId');
 f.p.shops=async()=>({shops:f.list.slice(0,1)});let feeCalls=0;
 f.p.services=async id=>{assert.equal(id,shopId);return [{service_id:20,service_type_id:2}];};
 f.p.fee=async id=>{assert.equal(id,shopId);feeCalls++;return {total:25000};};
 const buyer=f.uid+'buyer',post=f.uid+'post';
 await db.doc(`users/${buyer}/addresses/home`).set(f.source);
 await db.doc(`posts/${post}`).set({ownerId:f.uid,type:'SALE',status:'ACTIVE',title:'Clothing',price:10000});
 await db.doc('shippingConfig/ghn_staging').set({environment:'staging',parcelEstimate:{weight:300,length:25,width:20,height:3,estimated:true}});
 const checkout=require('../ghn-checkout')({...f.context,postOwnerId:p=>p.get('ownerId'),postType:p=>p.get('type'),orderPostSnapshot:p=>({title:p.get('title')}),cleanText:v=>v});
 const request={auth:{uid:buyer},data:{postId:post,addressId:'home'}};
 assert.equal((await checkout.quote(request)).total,35000);assert.equal(feeCalls,1);
 await db.doc(`ghnSellerRegistrations/staging_${f.uid}`).update({registeredShopId:999999});
 await assert.rejects(checkout.quote(request),e=>e.code==='failed-precondition');assert.equal(feeCalls,1);
 await db.doc(`ghnSellerRegistrations/staging_${f.uid}`).update({registeredShopId:shopId});
 f.p.shops=async()=>({shops:[{_id:shopId,district_id:2,ward_code:'3',address:'Wrong physical pickup'}]});
 await assert.rejects(checkout.quote(request),e=>e.code==='failed-precondition');assert.equal(feeCalls,1);
});

test('changing pickup clears old create evidence before a new uncertain registration',async()=>{
 const f=await fixture();await f.setup(f.request);const oldShop=(await f.mapping()).get('shopId');
 f.advance(61000);await db.doc(`users/${f.uid}/addresses/home`).update({street:'A new pickup'});f.setMode('unknown');
 await assert.rejects(f.setup(f.request));const operation=await f.operation();
 assert.equal(operation.get('creationConfirmed'),false);assert.equal(operation.get('registeredShopId'),undefined);
 assert.equal(await f.recover(),false);assert.equal((await f.mapping()).get('shopId'),oldShop);assert.equal((await f.mapping()).get('pickup').street,'22 Test Road');
});
