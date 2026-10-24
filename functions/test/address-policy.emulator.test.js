const {test,after}=require('node:test');
const assert=require('node:assert/strict');
process.env.GCLOUD_PROJECT='fashion2hand';
process.env.FIREBASE_CONFIG=JSON.stringify({projectId:'fashion2hand'});
process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099';
process.env.FUNCTIONS_EMULATOR='true';
const h=require(process.env.FASHION_TEST_FUNCTIONS || '../index');
const {getFirestore}=require('firebase-admin/firestore');
const {getAuth}=require('firebase-admin/auth');
const {deleteApp,getApp}=require('firebase-admin/app');
const db=getFirestore(),auth=getAuth();let uid;
after(async()=>{if(uid){await db.recursiveDelete(db.doc(`users/${uid}`));await auth.deleteUser(uid);}await deleteApp(getApp());});
test('two-level address directory, editable recipients and default address lifecycle',async()=>{
 const data=JSON.parse(require('node:fs').readFileSync(require.resolve('../data/vietnam-addresses.json'),'utf8').replace(/^\uFEFF/,''));
 assert.equal(data.length,34);const codes=data.flatMap(p=>p.wards.map(w=>w.code));assert.equal(codes.length,3321);assert.equal(new Set(codes).size,3321);
 const u=await auth.createUser({email:`address-${Date.now()}@example.test`,emailVerified:true});uid=u.uid;
 await db.doc(`users/${uid}`).set({uid,role:'USER',accountStatus:'ACTIVE',name:'Profile name',phone:'0901234567'});
 const request=(id,address)=>({auth:{uid,token:{}},data:{id,address}});
 const base={recipient:'Other recipient',phone:'0987654321',street:'12 Test',provinceCode:String(data[0].code),wardCode:String(data[0].wards[0].code),province:'forged',ward:'forged',isDefault:false};
 await h.saveAddress.run(request('home',base));
 let saved=(await db.doc(`users/${uid}/addresses/home`).get()).data();
 assert.equal(saved.province,data[0].name);assert.equal(saved.ward,data[0].wards[0].name);assert.equal(saved.district,'');assert.equal(saved.isDefault,true);assert.equal(saved.recipient,'Other recipient');
 await assert.rejects(h.saveAddress.run(request('bad',{...base,wardCode:String(data[1].wards[0].code)})),e=>e.code==='invalid-argument');
 await h.saveAddress.run(request('work',{...base,recipient:'Colleague',label:'WORK',isDefault:true}));
 assert.equal((await db.doc(`users/${uid}/addresses/home`).get()).get('isDefault'),false);
 await h.saveAddress.run(request('home',{...base,recipient:'Edited recipient'}));
 assert.equal((await db.doc(`users/${uid}/addresses/home`).get()).get('recipient'),'Edited recipient');
 assert.equal((await db.doc(`users/${uid}`).get()).get('name'),'Profile name');
 await h.deleteAddress.run({auth:{uid,token:{}},data:{id:'work'}});
 assert.equal((await db.doc(`users/${uid}/addresses/home`).get()).get('isDefault'),true);
 const posts=[];
 for(const type of ['SALE','SALE','FREE']){const post=db.collection('posts').doc();posts.push(post.id);await post.set({ownerId:'address-fixture-seller',title:'Address checkout fixture',type,price:type==='SALE'?120000:0,status:'ACTIVE',condition:'GOOD'});}
 try{
  const req=data=>({auth:{uid,token:{email_verified:true}},data});
  assert.ok((await h.createPaidOrder.run(req({postId:posts[0],addressId:'home',idempotencyKey:'address-paid'}))).orderId);
  assert.ok((await h.createFreeRequest.run(req({postId:posts[2],addressId:'home',note:'Test'}))).requestId);
  await db.doc(`users/${uid}/cartItems/${posts[1]}`).set({postId:posts[1]});
  const quote=await h.getCartCheckoutQuote.run(req({postIds:[posts[1]]}));
  const cart=await h.createCartOrders.run(req({postIds:[posts[1]],addressId:'home',idempotencyKey:'address-cart',expectedTotal:quote.total}));
  assert.equal(cart.orderIds.length,1);
  const order=await db.doc(`orders/${cart.orderIds[0]}`).get();
  assert.equal(order.get('shippingAddress.district'),'');assert.equal(order.get('shippingAddress.wardCode'),String(data[0].wards[0].code));
 }finally{await Promise.all(posts.map(id=>db.doc(`posts/${id}`).delete()));}
});
