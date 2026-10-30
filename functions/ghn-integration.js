const { GhnProvider, GhnError } = require('./ghn-provider');

/** Installs GHN routes on the existing callables; emulator-only mocks remain explicit. */
module.exports = ctx => {
  const { exports: endpoints, db, onCall, onRequest, defineSecret, fail, uidOf, cleanDocumentId, FieldValue, logger }=ctx;
  const token=defineSecret('GHN_TOKEN');
  const provider=()=>new GhnProvider({token:()=>token.value(),log:entry=>logger.warn('GHN request outcome',entry)});
  const quote=require('./ghn-checkout')({...ctx,provider});
  const shipment=require('./ghn-shipments')({...ctx,provider});
  const options={secrets:[token],timeoutSeconds:120};
  const selected=()=>process.env.SHIPPING_PROVIDER==='GHN';
  const allowedMock=()=>process.env.FUNCTIONS_EMULATOR==='true'&&process.env.ENABLE_SIMULATED_SHIPPING==='true';
  const safe=fn=>async request=>{try{return await fn(request);}catch(error){if(error instanceof GhnError)fail(error.code,error.message,error.details);throw error;}};
  function route(name,handler){const previous=endpoints[name];endpoints[name]=onCall(options,safe(request=>{
    uidOf(request);
    if(selected())return handler(request);
    if(allowedMock())return previous.run(request);
    fail('failed-precondition','Chưa cấu hình vận chuyển. Đặt SHIPPING_PROVIDER=GHN và cấu hình môi trường, Secret, shop/điểm gửi.');
  }));}
  route('getCheckoutQuote',quote.quote);route('getCartCheckoutQuote',quote.quote);
  route('createPaidOrder',r=>quote.createOrders(r,true));route('createCartOrders',r=>quote.createOrders(r,false));
  route('createFreeRequest',quote.createFree);route('createShipment',shipment.create);
  const respond=endpoints.respondFreeRequest;
  const acceptAndShip=require('./free-shipping')({checkout:quote,shipment,db,FieldValue});
  endpoints.respondFreeRequest=onCall(options,safe(r=>selected()&&r.data.accept===true?acceptAndShip(r):respond.run(r)));
  const refresh=endpoints.refreshOrder;
  endpoints.refreshOrder=onCall(options,safe(async r=>{
    const uid=uidOf(r),id=cleanDocumentId(r.data.orderId,'Đơn hàng'),order=await db.doc(`orders/${id}`).get();
    if(!order.exists||![order.get('buyerId'),order.get('sellerId')].includes(uid))fail('permission-denied','Không có quyền xem đơn.');
    const shipping=order.get('shippingProvider')==='GHN'?await shipment.refresh(r):null;
    const result=await refresh.run(r);
    return shipping?{...result,ghn:shipping}:result;
  }));
  const capabilities=endpoints.getTransactionCapabilities;
  endpoints.getTransactionCapabilities=onCall(async r=>{
    uidOf(r);if(!selected())return allowedMock()?capabilities.run(r):{shippingProvider:'UNCONFIGURED',shippingFee:null,simulatedShipping:false,simulatedPayments:false,paymentMode:'SIMULATED'};
    return {shippingProvider:'GHN',shippingEnvironment:process.env.GHN_ENV||'UNCONFIGURED',requiresShippingAddress:true,
      shippingFee:null,simulatedShipping:false,simulatedPayments:process.env.FUNCTIONS_EMULATOR==='true'&&process.env.ENABLE_SIMULATED_PAYMENTS==='true',
      checkoutItemsPerOrder:20,checkoutGrouping:'SELLER',liveProvidersConnected:false,productionMutationsEnabled:process.env.GHN_PRODUCTION_ENABLED==='true',paymentMode:'SIMULATED'};
  });
  endpoints.cancelGhnShipment=onCall(options,safe(shipment.cancel));
  endpoints.ghnCallback=onRequest({...options,maxInstances:3},shipment.callback);
  const shopId=()=>{const id=Number(process.env.GHN_SHOP_ID);if(!Number.isSafeInteger(id)||id<=0)fail('failed-precondition','Chưa cấu hình GHN_SHOP_ID cho danh mục địa chỉ.');return id;};
  const setupPickup=require('./ghn-seller-onboarding')({...ctx,provider,bootstrapShopId:shopId});
  endpoints.setupGhnSellerPickup=onCall(options,safe(setupPickup));
  async function admin(request){const uid=uidOf(request),user=await db.doc(`users/${uid}`).get(),authUser=await ctx.getAuth().getUser(uid);if(authUser.disabled||authUser.customClaims?.admin!==true||!user.exists||user.get('role')!=='ADMIN'||user.get('accountStatus')!=='ACTIVE')fail('permission-denied','Chỉ quản trị viên có claim admin được cấu hình GHN.');}
  endpoints.ghnAdminShops=onCall(options,safe(async r=>{await admin(r);return {environment:provider().settings.environment,...await provider().shops(Number.isSafeInteger(r.data.offset)&&r.data.offset>=0?r.data.offset:0)};}));
  endpoints.ghnAddressCatalog=onCall(options,safe(async r=>{
    uidOf(r);const p=provider(),id=shopId(),kind=r.data.kind;
    if(kind==='provinces')return {environment:p.settings.environment,items:await p.provinces(id)};
    if(kind==='districts'&&Number.isSafeInteger(r.data.provinceId)&&r.data.provinceId>0)return {environment:p.settings.environment,items:await p.districts(id,r.data.provinceId)};
    if(kind==='wards'&&Number.isSafeInteger(r.data.districtId)&&r.data.districtId>0)return {environment:p.settings.environment,items:await p.wards(id,r.data.districtId)};
    fail('invalid-argument','Chọn provinces, districts hoặc wards với mã GHN hợp lệ.');
  }));
  endpoints.saveGhnAddress=onCall(options,safe(async r=>{
    const uid=uidOf(r),id=cleanDocumentId(r.data.addressId,'Địa chỉ'),ref=db.doc(`users/${uid}/addresses/${id}`),address=await ref.get();
    if(!address.exists)fail('not-found','Chọn địa chỉ đã lưu thuộc tài khoản của bạn.');
    const contact={};
    if(r.data.contact!==undefined) {
      if(!r.data.contact || typeof r.data.contact!=='object' || Array.isArray(r.data.contact))fail('invalid-argument','Thông tin địa chỉ không hợp lệ.');
      for(const [key,max] of [['recipient',100],['phone',20],['street',300]]) {
        const value=r.data.contact[key];
        if(typeof value!=='string'||!value.trim()||value.length>max)fail('invalid-argument','Nhập đủ tên, điện thoại và số nhà/đường hợp lệ.');
        contact[key]=value.trim();
      }
    }
    const p=provider(),canonical=await p.address(shopId(),{...address.data(),...contact,ghn:{...r.data.ghn,environment:p.settings.environment}});
    await db.runTransaction(async tx=>{const current=await tx.get(ref);if(!current.exists||JSON.stringify(current.data())!==JSON.stringify(address.data()))fail('failed-precondition','Địa chỉ vừa đổi. Chọn lại.');
      const mappingRef=db.doc(`ghnSellerShops/${p.settings.environment}_${uid}`),mapping=await tx.get(mappingRef);
      if(mapping.exists&&mapping.get('addressId')===id) {
        const update=require('./ghn-pickup-policy').pickupMappingUpdate(mapping.data(),canonical);
        if(update)tx.update(mappingRef,{...update,updatedAt:FieldValue.serverTimestamp()});
      }
      tx.update(ref,{...contact,ghn:canonical.ghn,ghnDisplay:{province:canonical.province,district:canonical.district,ward:canonical.ward},updatedAt:FieldValue.serverTimestamp()});});return {ok:true};
  }));
  endpoints.ghnConfigureSeller=onCall(options,safe(async r=>{
    await admin(r);const p=provider(),environment=p.settings.environment,sellerId=cleanDocumentId(r.data.sellerId,'Người bán'),addressId=cleanDocumentId(r.data.addressId,'Địa chỉ gửi');
    if(!Number.isSafeInteger(r.data.shopId)||r.data.shopId<=0)fail('invalid-argument','Shop ID phải là số nguyên dương thuộc môi trường đã chọn.');
    const source=await db.doc(`users/${sellerId}/addresses/${addressId}`).get();if(!source.exists)fail('not-found','Không tìm thấy địa chỉ của seller.');
    const pickup=await p.address(r.data.shopId,source.data());let found=null,offset=0;
    for(let page=0;page<20&&!found;page++){const result=await p.shops(offset);found=result.shops.find(s=>s._id===r.data.shopId);if(result.shops.length<50||result.last_offset===offset)break;offset=result.last_offset;}
    if(!found||found.district_id!==pickup.ghn.districtId||found.ward_code!==pickup.ghn.wardCode)fail('failed-precondition','Shop không thuộc Token/môi trường hoặc không khớp điểm gửi seller đã chọn.');
    if(r.data.confirmRegisteredPickup!==true)fail('failed-precondition','Quản trị viên phải xác nhận địa chỉ GHN đăng ký là điểm lấy hàng thực tế của seller.');
    pickup.street=found.address;
    await db.doc(`ghnSellerShops/${environment}_${sellerId}`).set({environment,sellerId,shopId:r.data.shopId,addressId,pickup,registeredShopAddress:found.address,enabled:true,updatedAt:FieldValue.serverTimestamp()});
    return {ok:true,environment,sellerId,shopId:r.data.shopId,pickup};
  }));
  endpoints.ghnConfigureParcel=onCall(options,safe(async r=>{
    await admin(r);const estimate=r.data.parcelEstimate;require('./ghn-provider').parcel([{}],estimate);const environment=provider().settings.environment;
    await db.doc(`shippingConfig/ghn_${environment}`).set({environment,parcelEstimate:{weight:estimate.weight,length:estimate.length,width:estimate.width,height:estimate.height,estimated:true},updatedAt:FieldValue.serverTimestamp()});return {ok:true};
  }));
};
