'use strict';
const {hash,GhnError}=require('./ghn-provider');
const {normalizeStreet}=require('./ghn-pickup-policy');
const phone=value=>String(value||'').replace(/\D/g,'').replace(/^84(?=\d{9}$)/,'0');
const samePoint=(shop,pickup)=>shop&&shop.district_id===pickup.ghn.districtId&&String(shop.ward_code)===pickup.ghn.wardCode&&normalizeStreet(shop.address)===normalizeStreet(pickup.street);
const identity=(uid,pickup)=>hash(JSON.stringify([uid,normalizeStreet(pickup.street),pickup.ghn.provinceId,pickup.ghn.districtId,pickup.ghn.wardCode,phone(pickup.phone)]));
const receiptShop=(op,uid,pickup)=>{
 const fingerprint=identity(uid,pickup),name=`F2-${hash(uid).slice(0,12)}-${fingerprint.slice(0,16)}`;
 if(op.creationConfirmed!==true||!Number.isSafeInteger(op.registeredShopId)||op.registeredShopId<=0||op.fingerprint!==fingerprint||op.name!==name)return null;
 return {_id:op.registeredShopId,name,phone:pickup.phone,address:pickup.street,district_id:pickup.ghn.districtId,ward_code:pickup.ghn.wardCode};
};
 async function shops(p,fail){
  const all=[];let offset=0;
  for(let page=0;page<20;page++){
   const result=await p.shops(offset);
   if(!Array.isArray(result?.shops))fail('unavailable','Chưa đọc được danh sách điểm lấy hàng GHN.');
   all.push(...result.shops);
   if(result.shops.length<50)return all;
   const next=Number.isSafeInteger(result.last_offset)?result.last_offset:offset+50;
   if(next<=offset)fail('unavailable','Danh sách GHN chưa đầy đủ; chưa tạo điểm lấy hàng mới.');
   offset=next;
  }
  fail('resource-exhausted','Danh sách điểm lấy hàng quá lớn; cần hỗ trợ từ quản trị viên.');
 }
module.exports=({db,FieldValue,fail,uidOf,cleanDocumentId,requireVerifiedAccount,provider,bootstrapShopId,logger,now=()=>Date.now()})=>{
 return async request=>{
  const uid=uidOf(request);await requireVerifiedAccount(request);
  const p=provider(),environment=p.settings.environment;
  if(environment!=='staging')fail('failed-precondition','Tự thiết lập điểm lấy hàng hiện chỉ mở trên GHN staging.');
  if(request.data.confirmPickup!==true)fail('invalid-argument','Xác nhận đây là địa chỉ GHN đến lấy hàng của bạn.');
  const addressId=cleanDocumentId(request.data.addressId,'Địa chỉ lấy hàng'),baseId=bootstrapShopId();
  const addressRef=db.doc(`users/${uid}/addresses/${addressId}`),source=await addressRef.get();
  if(!source.exists)fail('not-found','Chọn địa chỉ đã lưu trong tài khoản của bạn.');
  const pickup=await p.address(baseId,source.data());
  const fingerprint=hash(JSON.stringify([uid,normalizeStreet(pickup.street),pickup.ghn.provinceId,pickup.ghn.districtId,pickup.ghn.wardCode,phone(pickup.phone)]));
  const name=`F2-${hash(uid).slice(0,12)}-${fingerprint.slice(0,16)}`;
  const mappingRef=db.doc(`ghnSellerShops/${environment}_${uid}`),opRef=db.doc(`ghnSellerRegistrations/${environment}_${uid}`);
  const owner=hash(`${fingerprint}:${now()}:${Math.random()}`),day=new Date(now()).toISOString().slice(0,10);
  const prior=await db.runTransaction(async tx=>{
   const [operation,user]=await Promise.all([tx.get(opRef),tx.get(db.doc(`users/${uid}`))]);
   if(!user.exists||user.get('accountStatus')!=='ACTIVE')fail('permission-denied','Tài khoản chưa đủ điều kiện thiết lập vận chuyển.');
   const old=operation.exists?operation.data():{};
   if(old.leaseUntil?.toMillis?.()>now())fail('aborted','Đang thiết lập điểm lấy hàng. Vui lòng chờ rồi thử lại.');
   if(['REQUESTING','UNKNOWN'].includes(old.state)&&old.fingerprint!==fingerprint)fail('failed-precondition','Điểm lấy hàng trước đang chờ GHN xác nhận. Chọn lại địa chỉ đó để kiểm tra trước.');
   tx.set(opRef,{...old,owner,leaseUntil:new Date(now()+120000),updatedAt:FieldValue.serverTimestamp()});return old;
  });
  let attempted=false,mutationReturned=false,phase='READ_SHOPS';
  try{
   const all=await shops(p,fail),mapping=await mappingRef.get();
   let found=mapping.exists&&mapping.get('environment')===environment?all.find(s=>s._id===mapping.get('shopId')&&samePoint(s,pickup)):null;
   let fromCreationReceipt=false;
   if(!found)found=all.find(s=>s.name===name&&phone(s.phone)===phone(pickup.phone)&&samePoint(s,pickup));
   if(!found){found=receiptShop(prior,uid,pickup);fromCreationReceipt=!!found;}
   if(!found){
    if(['REQUESTING','UNKNOWN'].includes(prior.state))fail('unavailable','GHN chưa trả kết quả tạo điểm lấy hàng trước. Chưa gửi lại để tránh tạo trùng; vui lòng kiểm tra lại sau.');
    const requestedAt=prior.lastRequestedAt?.toMillis?.()||0;
    if(requestedAt>now()-60000)fail('resource-exhausted','Vui lòng chờ một phút trước khi đăng ký điểm lấy hàng mới.');
    const count=prior.day===day?(prior.dailyCount||0):0;
    if(count>=5)fail('resource-exhausted','Bạn đã đạt giới hạn 5 điểm lấy hàng mới trong ngày.');
    await db.runTransaction(async tx=>{const operation=await tx.get(opRef);if(operation.get('owner')!==owner)fail('aborted','Một yêu cầu khác đang thiết lập điểm lấy hàng.');
     tx.update(opRef,{fingerprint,name,addressId,state:'REQUESTING',creationConfirmed:false,registeredShopId:FieldValue.delete(),registrationResponseShape:FieldValue.delete(),day,dailyCount:count+1,lastRequestedAt:new Date(now())});});
    attempted=true;
    phase='REGISTER_SHOP';
    const result=await p.registerShop(baseId,{name,phone:pickup.phone,address:pickup.street,ward_code:pickup.ghn.wardCode,district_id:pickup.ghn.districtId});
    mutationReturned=true;
    const registeredId=typeof result?.shop_id==='string'&&/^\d+$/.test(result.shop_id)?Number(result.shop_id):result?.shop_id;
    const responseShape={type:typeof result,array:Array.isArray(result),shopIdType:typeof result?.shop_id};
    if(logger)logger.info('GHN pickup register response',{environment,responseShape,validShopId:Number.isSafeInteger(registeredId)&&registeredId>0});
    if(!Number.isSafeInteger(registeredId)||registeredId<=0)throw new GhnError('unavailable','GHN chưa trả mã điểm lấy hàng hợp lệ. Kiểm tra lại để đối soát.',true,{reason:'GHN_REGISTER_SHOP_ID_MISSING'});
    await db.runTransaction(async tx=>{const operation=await tx.get(opRef);if(operation.get('owner')!==owner)fail('aborted','Thiết lập đã thay đổi. Kiểm tra lại điểm lấy hàng.');
     tx.update(opRef,{registeredShopId:registeredId,creationConfirmed:true,registrationResponseShape:responseShape});});
    // GHN's successful server-to-server create response is authoritative.
    // shop/all can remain stale after creation; do not make linkage depend on its read-after-write visibility.
    found={_id:registeredId,name,phone:pickup.phone,address:pickup.street,district_id:pickup.ghn.districtId,ward_code:pickup.ghn.wardCode};
    fromCreationReceipt=true;
   }
   const canonical={...pickup,street:found.address};
   phase='LINK_FIRESTORE';
   await db.runTransaction(async tx=>{
    const [current,operation,user]=await Promise.all([tx.get(addressRef),tx.get(opRef),tx.get(db.doc(`users/${uid}`))]);
    if(!current.exists||!current.updateTime.isEqual(source.updateTime))fail('failed-precondition','Địa chỉ vừa thay đổi. Lưu lại và kiểm tra điểm lấy hàng.');
    if(operation.get('owner')!==owner||user.get('accountStatus')!=='ACTIVE')fail('aborted','Thiết lập đã thay đổi. Vui lòng kiểm tra lại.');
    tx.set(mappingRef,{environment,sellerId:uid,shopId:found._id,addressId,pickup:canonical,registeredShopAddress:found.address,enabled:true,source:'SELLER_SELF_SERVICE',verificationSource:fromCreationReceipt?'GHN_CREATE_RESPONSE':'GHN_SHOP_LIST',updatedAt:FieldValue.serverTimestamp()});
    tx.set(opRef,{state:'LINKED',fingerprint,name,addressId,shopId:found._id,leaseUntil:FieldValue.delete(),updatedAt:FieldValue.serverTimestamp()},{merge:true});
   });
   return {ok:true,environment,addressId,pickup:canonical};
  }catch(error){
   await db.runTransaction(async tx=>{const op=await tx.get(opRef);if(op.get('owner')!==owner)return;
    const uncertain=attempted&&(mutationReturned||!(error instanceof GhnError)||error.uncertain===true);
    tx.update(opRef,{...(attempted?{state:uncertain?'UNKNOWN':'REJECTED'}:{}),lastFailurePhase:phase,leaseUntil:FieldValue.delete(),updatedAt:FieldValue.serverTimestamp()});
   });if(logger)logger.warn('GHN pickup setup outcome',{environment,phase,code:error instanceof GhnError?error.code:'SETUP_NOT_COMPLETED'});throw error;
  }
 };
};
// Quote recovery only links an already-created, exactly matching shop. It never registers a new shop.
module.exports.recover=async({db,FieldValue,provider,bootstrapShopId,now=()=>Date.now()},uid)=>{
 const p=provider();if(p.settings.environment!=='staging')return false;
 const opRef=db.doc(`ghnSellerRegistrations/staging_${uid}`),operation=await opRef.get();
 if(!operation.exists||!['UNKNOWN','REQUESTING'].includes(operation.get('state'))||operation.get('leaseUntil')?.toMillis?.()>now())return false;
 const id=operation.get('addressId');if(typeof id!=='string'||!id||id.includes('/'))return false;
 const addressRef=db.doc(`users/${uid}/addresses/${id}`),source=await addressRef.get(),userRef=db.doc(`users/${uid}`),user=await userRef.get();
 if(!source.exists||!user.exists||user.get('accountStatus')!=='ACTIVE')return false;
 const base=bootstrapShopId();if(!Number.isSafeInteger(base)||base<=0)return false;
 const pickup=await p.address(base,source.data());
 const fingerprint=hash(JSON.stringify([uid,normalizeStreet(pickup.street),pickup.ghn.provinceId,pickup.ghn.districtId,pickup.ghn.wardCode,phone(pickup.phone)]));
 const name=`F2-${hash(uid).slice(0,12)}-${fingerprint.slice(0,16)}`;
 if(operation.get('fingerprint')!==fingerprint||operation.get('name')!==name)return false;
 const fail=(code,message)=>{throw new GhnError(code,message);};
 const all=await shops(p,fail);let found=all.find(s=>s.name===name&&phone(s.phone)===phone(pickup.phone)&&samePoint(s,pickup));
 const fromCreationReceipt=!found&&!!receiptShop(operation.data(),uid,pickup);
 if(!found&&fromCreationReceipt)found=receiptShop(operation.data(),uid,pickup);
 if(!found)return false;
 return db.runTransaction(async tx=>{
  const mappingRef=db.doc(`ghnSellerShops/staging_${uid}`),[currentOp,currentAddress,currentUser,mapping]=await Promise.all([tx.get(opRef),tx.get(addressRef),tx.get(userRef),tx.get(mappingRef)]);
  if(!currentOp.exists||!currentOp.updateTime.isEqual(operation.updateTime)||!currentAddress.exists||!currentAddress.updateTime.isEqual(source.updateTime)||currentUser.get('accountStatus')!=='ACTIVE')return false;
  if(mapping.exists&&mapping.get('enabled')===true)return true;
  tx.set(mappingRef,{environment:'staging',sellerId:uid,shopId:found._id,addressId:id,pickup:{...pickup,street:found.address},registeredShopAddress:found.address,enabled:true,source:'SELLER_SELF_SERVICE_RECOVERED',verificationSource:fromCreationReceipt?'GHN_CREATE_RESPONSE':'GHN_SHOP_LIST',updatedAt:FieldValue.serverTimestamp()});
  tx.update(opRef,{state:'LINKED',shopId:found._id,leaseUntil:FieldValue.delete(),updatedAt:FieldValue.serverTimestamp()});return true;
 });
};
module.exports.creationReceiptMatches=(mapping,operation,uid)=>{
 if(mapping?.environment!=='staging'||mapping.sellerId!==uid||mapping.verificationSource!=='GHN_CREATE_RESPONSE'||operation?.state!=='LINKED'||mapping.pickup?.ghn?.environment!=='staging')return false;
 const receipt=receiptShop(operation,uid,mapping.pickup);
 return !!receipt&&receipt._id===mapping.shopId&&samePoint(receipt,mapping.pickup)&&normalizeStreet(mapping.registeredShopAddress)===normalizeStreet(receipt.address);
};
