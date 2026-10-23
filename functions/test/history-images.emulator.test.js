const {test,after}=require('node:test');
const assert=require('node:assert/strict');
process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099';
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getFirestore}=require('firebase-admin/firestore');
const app=initializeApp({projectId:'fashion2hand'},`history-photo-${Date.now()}`),db=getFirestore(app);
after(()=>deleteApp(app));
async function user(){const response=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({returnSecureToken:true})});assert.equal(response.status,200);const data=await response.json();await db.doc(`users/${data.localId}`).set({uid:data.localId,role:'USER',accountStatus:'ACTIVE'});return {uid:data.localId,token:data.idToken};}
test('reserved and sold history photos are readable only by the seller and buyer',async()=>{
 const seller=await user(),buyer=await user(),outsider=await user();
 const post=db.collection('posts').doc(),order=db.collection('orders').doc();
 await post.set({ownerId:seller.uid,status:'ACTIVE'});
 const object=encodeURIComponent(`posts/${post.id}/images/history.jpg`),url=`http://127.0.0.1:9199/v0/b/fashion2hand.firebasestorage.app/o/${object}?alt=media`;
 const upload=await fetch(`http://127.0.0.1:9199/v0/b/fashion2hand.firebasestorage.app/o?uploadType=media&name=${object}`,{method:'POST',headers:{authorization:`Firebase ${seller.token}`,'content-type':'image/jpeg'},body:Buffer.from([255,216,255,217])});assert.equal(upload.status,200,await upload.text());
 const read=actor=>fetch(url,{headers:actor?{authorization:`Firebase ${actor.token}`}:{}});
 assert.equal((await read(null)).status,200);
 await order.set({buyerId:buyer.uid,sellerId:seller.uid});
 for(const status of ['RESERVED','SOLD']){
  await post.update({status,reservedForOrderId:order.id});
  assert.equal((await read(buyer)).status,200,status);assert.equal((await read(seller)).status,200,status);
  assert.equal((await read(outsider)).status,403,status);assert.equal((await read(null)).status,403,status);
 }
});