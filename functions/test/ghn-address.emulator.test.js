const {test,after}=require('node:test');
const assert=require('node:assert/strict');
process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:8080';
process.env.GHN_ENV='staging';process.env.GHN_TOKEN_ENV='staging';process.env.GHN_SHOP_ID='10';
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
const app=initializeApp({projectId:'demo-fashion2hand-address-edit'},'address-edit'),db=getFirestore(app);
const {GhnProvider}=require('../ghn-provider');
const original=GhnProvider.prototype.address;
GhnProvider.prototype.address=async function(shop,source){
 assert.equal(shop,10);
 return {...source,province:'Province',district:'District',ward:'Ward',ghn:{...source.ghn,addressModel:'GHN_LEGACY'}};
};
after(async()=>{GhnProvider.prototype.address=original;await deleteApp(app);});
const endpoints={},fail=(code,message)=>{throw Object.assign(new Error(message),{code});};
require('../ghn-integration')({exports:endpoints,db,FieldValue,fail,
 onCall:(options,handler)=>handler||options,onRequest:(options,handler)=>handler,
 defineSecret:()=>({value:()=>''}),logger:{warn:()=>{}},
 uidOf:r=>{if(!r.auth?.uid)fail('unauthenticated','Sign in');return r.auth.uid;},
 cleanDocumentId:id=>{if(typeof id!=='string'||!id||id.includes('/'))fail('invalid-argument','ID');return id;}});
test('changing the physical GHN pickup is atomic, invalidates linked shop and preserves other address fields',async()=>{
 const uid=`seller-${Date.now()}`,ref=db.doc(`users/${uid}/addresses/home`),mapping=db.doc(`ghnSellerShops/staging_${uid}`);
 const ghn={provinceId:1,districtId:2,wardCode:'3',environment:'staging',addressModel:'GHN_LEGACY'};
 const contact={recipient:'Seller',phone:'0901234567',street:'Old street'};
 await ref.set({...contact,ghn,isDefault:true,provinceCode:'79'});
 await mapping.set({enabled:true,addressId:'home',pickup:{...contact,ghn}});
 const request=contact=>({auth:{uid},data:{addressId:'home',ghn,contact}});
 await endpoints.saveGhnAddress(request(contact));
 assert.equal((await mapping.get()).get('enabled'),true);
 await endpoints.saveGhnAddress(request({...contact,street:'New street'}));
 assert.equal((await ref.get()).get('street'),'New street');
 assert.equal((await ref.get()).get('isDefault'),true);
 assert.equal((await mapping.get()).get('enabled'),false);
 await assert.rejects(endpoints.saveGhnAddress(request({...contact,street:''})),{code:'invalid-argument'});
 await assert.rejects(endpoints.saveGhnAddress({auth:{uid:'outsider'},data:{addressId:'home',ghn,contact}}),{code:'not-found'});
 assert.equal((await ref.get()).get('street'),'New street');
});

test('resaving the same pickup with accents or updated contact preserves the approved shop',async()=>{
 const uid=`same-pickup-${Date.now()}`,ref=db.doc(`users/${uid}/addresses/home`),mapping=db.doc(`ghnSellerShops/staging_${uid}`);
 const ghn={provinceId:202,districtId:1450,wardCode:'20804',environment:'staging',addressModel:'GHN_LEGACY'};
 const contact={recipient:'Seller',phone:'0901234567',street:'222 Ta Quang Buu'};
 await ref.set({...contact,ghn,isDefault:true});
 await mapping.set({enabled:true,addressId:'home',shopId:10,registeredShopAddress:'222 Tạ Quang Bửu',pickup:{...contact,street:'222 Tạ Quang Bửu',ghn}});
 await endpoints.saveGhnAddress({auth:{uid},data:{addressId:'home',ghn,contact:{...contact,recipient:'Updated name',phone:'0912345678',street:'222 TẠ QUANG BỬU.'}}});
 const saved=await mapping.get();assert.equal(saved.get('enabled'),true);
 assert.equal(saved.get('shopId'),10);assert.equal(saved.get('pickup.street'),'222 Tạ Quang Bửu');
 assert.equal(saved.get('pickup.phone'),'0912345678');assert.equal(saved.get('pickup.recipient'),'Updated name');
});
