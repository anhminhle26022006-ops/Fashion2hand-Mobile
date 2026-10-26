const { hash, parcel, GhnError } = require('./ghn-provider');

module.exports = ({ db, FieldValue, fail, uidOf, cleanDocumentId, cleanText, requireVerifiedAccount, postOwnerId, postType, orderPostSnapshot, writeOrderEvent, provider }) => {
  const env = () => provider().settings.environment;
  const idsOf = data => {
    const ids = data.postIds || [data.postId];
    if (!Array.isArray(ids) || !ids.length || ids.length > 20) fail('invalid-argument', 'Chọn 1–20 món.');
    const sorted = ids.map(id => cleanDocumentId(id, 'Món đồ')).sort();
    if (new Set(sorted).size !== sorted.length) fail('invalid-argument', 'Món đồ bị lặp.');
    return sorted;
  };
  const view = post => ({ postId: post.id, sellerId: postOwnerId(post), type: postType(post), price: postType(post) === 'FREE' ? 0 : post.get('price'), ...orderPostSnapshot(post) });
  function inventory(posts, uid) {
    for (const post of posts) if (!post.exists || !['FREE','SALE'].includes(postType(post)) || postOwnerId(post) === uid || post.get('status') !== 'ACTIVE' || post.get('reservedForOrderId'))
      fail('failed-precondition', 'Món đồ không còn khả dụng hoặc thuộc chính bạn.');
    const items = posts.map(view);
    if (items.some(i => !Number.isSafeInteger(i.price) || i.price < 0 || i.type === 'SALE' && i.price === 0)) fail('failed-precondition','Giá sản phẩm không hợp lệ.');
    if (items.length > 1 && items.some(i => i.type !== 'SALE')) fail('invalid-argument','Giỏ nhiều món chỉ hỗ trợ bài bán.');
    return items;
  }
  function feePayload(group) {
    return { from_district_id: group.pickup.ghn.districtId, from_ward_code: group.pickup.ghn.wardCode,
      to_district_id: group.shippingAddress.ghn.districtId, to_ward_code: group.shippingAddress.ghn.wardCode,
      service_type_id: group.service.service_type_id, service_id: group.service.service_id,
      weight: group.parcel.weight, length: group.parcel.length, width: group.parcel.width, height: group.parcel.height,
      insurance_value: group.price };
  }
  async function context(uid, ids, addressId) {
    const p = provider(), environment = p.settings.environment;
    const [posts, address, settings] = await Promise.all([
      db.getAll(...ids.map(id=>db.doc(`posts/${id}`))), db.doc(`users/${uid}/addresses/${addressId}`).get(), db.doc(`shippingConfig/ghn_${environment}`).get()
    ]);
    if (!address.exists) fail('failed-precondition','Chọn địa chỉ nhận hàng thuộc tài khoản của bạn.');
    if (!settings.exists || settings.get('environment') !== environment) fail('failed-precondition','Chưa cấu hình shippingConfig cho đúng môi trường GHN.');
    const items = inventory(posts,uid), groups = new Map();
    for (const item of items) {
      if(!groups.has(item.sellerId))groups.set(item.sellerId,{sellerId:item.sellerId,items:[],price:0});
      const group=groups.get(item.sellerId);group.items.push(item);group.price+=item.price;
    }
    const source = { items, address: address.data(), settings: settings.data(), sellers: {} };
    for (const group of groups.values()) {
      const mapping=await db.doc(`ghnSellerShops/${environment}_${group.sellerId}`).get();
      if(!mapping.exists || mapping.get('environment')!==environment || mapping.get('enabled')!==true) fail('failed-precondition',`Người bán ${group.sellerId} chưa có điểm lấy hàng GHN được quản trị viên ánh xạ.`);
      const data=mapping.data();source.sellers[group.sellerId]=data;
      group.shopId=data.shopId;group.pickup=data.pickup;
      if(!Number.isSafeInteger(group.shopId)||!group.pickup?.ghn||group.pickup.ghn.environment!==environment)fail('failed-precondition','Cấu hình shop/điểm gửi của seller chưa hợp lệ hoặc sai môi trường.');
      // Check membership and exact registered pickup; never silently use the app owner's shop address.
      let offset=0,shop=null;
      for(let page=0;page<20&&!shop;page++){
        const result=await p.shops(offset);if(!Array.isArray(result.shops))throw new GhnError('unavailable','GHN trả danh sách shop không hợp lệ.');
        shop=result.shops.find(s=>s._id===group.shopId);if(result.shops.length<50||result.last_offset===offset)break;offset=result.last_offset;
      }
      if(!shop || shop.district_id!==group.pickup.ghn.districtId || shop.ward_code!==group.pickup.ghn.wardCode || shop.address!==data.registeredShopAddress)
        fail('failed-precondition','Token/Shop ID/điểm gửi không khớp môi trường hoặc hồ sơ GHN. Quản trị viên cần ánh xạ lại seller.');
      group.shippingAddress=await p.address(group.shopId,address.data());
      group.parcel=parcel(group.items,settings.get('parcelEstimate'));
      if(group.price>5000000)fail('failed-precondition','Giá trị khai báo vượt giới hạn 5.000.000đ của tích hợp GHN.');
      const services=await p.services(group.shopId,group.pickup.ghn.districtId,group.shippingAddress.ghn.districtId);
      if(!Array.isArray(services))throw new GhnError('unavailable','GHN trả danh sách dịch vụ không hợp lệ.');
      const service=services.find(s=>s.service_type_id===2&&Number.isSafeInteger(s.service_id)&&s.service_id>0);
      if(!service)fail('failed-precondition','Không có dịch vụ GHN phù hợp tuyến này.');
      group.service={service_id:service.service_id,service_type_id:service.service_type_id,short_name:service.short_name||'GHN'};
    }
    return { environment, source, groups:[...groups.values()].sort((a,b)=>a.sellerId.localeCompare(b.sellerId)) };
  }
  async function quote(request) {
    const uid=uidOf(request),ids=idsOf(request.data),addressId=cleanDocumentId(request.data.addressId,'Địa chỉ nhận');
    const reserved=await db.getAll(...ids.map(id=>db.doc(`posts/${id}`)));
    if(reserved.every(post=>post.exists&&typeof post.get('reservedForOrderId')==='string')){
      const orderIds=[...new Set(reserved.map(post=>post.get('reservedForOrderId')))];
      const orders=await db.getAll(...orderIds.map(id=>db.doc(`orders/${id}`)));
      if(orders.every(order=>order.exists&&order.get('buyerId')===uid&&order.get('orderStatus')==='PENDING_PAYMENT'))return orderIds.length===1?{existingOrderId:orderIds[0]}:{existingOrderIds:orderIds};
    }
    const data=await context(uid,ids,addressId),p=provider();
    for(const group of data.groups){const fee=await p.fee(group.shopId,feePayload(group));if(!Number.isSafeInteger(fee.total)||fee.total<0)throw new GhnError('unavailable','GHN trả phí không hợp lệ.');group.shippingFee=fee.total;}
    const quoteRef=db.collection('shippingQuotes').doc();
    const result={buyerId:uid,addressId,postIds:ids,environment:data.environment,sourceHash:hash(data.source),source:data.source,
      groups:data.groups,createdAt:FieldValue.serverTimestamp(),expiresAt:new Date(Date.now()+5*60*1000)};
    await quoteRef.create(result);
    return { quoteId:quoteRef.id,provider:'GHN',environment:data.environment,mode:data.environment==='staging'?'STAGING':'LIVE_SHIPPING',
      type:data.source.items[0].type,title:data.source.items.length===1?data.source.items[0].title:`${ids.length} món · ${data.groups.length} người bán`,
      groups:data.groups.map(g=>({sellerId:g.sellerId,items:g.items,price:g.price,shippingFee:g.shippingFee,service:g.service,parcel:g.parcel})),
      price:data.groups.reduce((s,g)=>s+g.price,0),shippingFee:data.groups.reduce((s,g)=>s+g.shippingFee,0),
      total:data.groups.reduce((s,g)=>s+g.price+g.shippingFee,0),currency:'VND',expiresAt:result.expiresAt.toISOString() };
  }
  async function loadQuote(request,{allowExpired=false}={}) {
    const uid=uidOf(request),quoteId=cleanDocumentId(request.data.quoteId,'Báo giá GHN'),doc=await db.doc(`shippingQuotes/${quoteId}`).get();
    if(!doc.exists||doc.get('buyerId')!==uid||doc.get('environment')!==env()||(!allowExpired&&doc.get('expiresAt').toMillis()<=Date.now()))fail('failed-precondition','Báo giá hết hạn hoặc không thuộc tài khoản. Lấy lại phí và xác nhận lại.');
    const q=doc.data();
    if(request.data.addressId&&request.data.addressId!==q.addressId)fail('failed-precondition','Địa chỉ đã đổi. Lấy lại báo giá.');
    if(request.data.postIds||request.data.postId)if(JSON.stringify(idsOf(request.data))!==JSON.stringify(q.postIds))fail('failed-precondition','Danh sách món đã đổi.');
    const current=await context(uid,q.postIds,q.addressId);
    if(hash(current.source)!==q.sourceHash)fail('failed-precondition','Thông tin món, địa chỉ hoặc kiện hàng đã thay đổi. Lấy lại báo giá.');
    for(const g of q.groups){const fee=await provider().fee(g.shopId,feePayload(g));if(fee.total!==g.shippingFee)fail('failed-precondition','Phí GHN đã thay đổi. Lấy lại báo giá và xác nhận tổng mới.');}
    return {id:quoteId,...q};
  }
  async function assertQuote(tx,q,uid) {
    const [quoteDoc,address,settings,...posts]=await Promise.all([tx.get(db.doc(`shippingQuotes/${q.id}`)),tx.get(db.doc(`users/${uid}/addresses/${q.addressId}`)),
      tx.get(db.doc(`shippingConfig/ghn_${q.environment}`)),...q.postIds.map(id=>tx.get(db.doc(`posts/${id}`)))]);
    if(!quoteDoc.exists||quoteDoc.get('expiresAt').toMillis()<=Date.now()||!address.exists||!settings.exists)fail('failed-precondition','Báo giá hết hạn hoặc địa chỉ đã bị xóa.');
    const source={items:inventory(posts,uid),address:address.data(),settings:settings.data(),sellers:{}};
    for(const g of q.groups){const seller=await tx.get(db.doc(`ghnSellerShops/${q.environment}_${g.sellerId}`));source.sellers[g.sellerId]=seller.exists?seller.data():null;}
    if(hash(source)!==q.sourceHash)fail('failed-precondition','Đầu vào đã thay đổi trong lúc đặt đơn. Lấy lại báo giá.');
  }
  async function createOrders(request,single) {
    await requireVerifiedAccount(request);const uid=uidOf(request),key=cleanText(request.data.idempotencyKey,128,'Mã yêu cầu'),ids=idsOf(request.data);
    if(single&&ids.length!==1)fail('invalid-argument','Luồng mua ngay chỉ nhận một món.');
    const ref=db.doc(`orderRequests/ghn_${hash([uid,key])}`),prior=await ref.get();
    const replay=doc=>{if(JSON.stringify(doc.get('postIds'))!==JSON.stringify(ids)||doc.get('addressId')!==request.data.addressId)fail('already-exists','Mã yêu cầu đã dùng cho đầu vào khác.');return single?{orderId:doc.get('orderIds')[0]}:{orderIds:doc.get('orderIds')};};
    if(prior.exists)return replay(prior);
    const q=await loadQuote(request);if(q.source.items.some(i=>i.type!=='SALE'))fail('failed-precondition','Chỉ đặt đơn mua cho bài bán.');
    return db.runTransaction(async tx=>{
      const previous=await tx.get(ref);if(previous.exists)return replay(previous);
      await assertQuote(tx,q,uid);
      const cart=request.data.postIds?await Promise.all(ids.map(id=>tx.get(db.doc(`users/${uid}/cartItems/${id}`)))):[];
      if(cart.some(d=>!d.exists))fail('failed-precondition','Món đã bị xóa khỏi giỏ.');
      const orderIds=[],now=FieldValue.serverTimestamp();
      for(const group of q.groups){
        const orderId=hash([uid,key,group.sellerId]),orderRef=db.doc(`orders/${orderId}`);orderIds.push(orderId);
        const first=group.items[0],shippingQuote={...group,environment:q.environment,quoteId:q.id,provider:'GHN'};
        tx.create(orderRef,{buyerId:uid,sellerId:group.sellerId,orderType:'PAID',postId:first.postId,postSnapshot:first,items:group.items,itemCount:group.items.length,schemaVersion:2,
          price:group.price,subtotal:group.price,shippingFee:group.shippingFee,totalAmount:group.price+group.shippingFee,shippingAddress:group.shippingAddress,pickupAddress:group.pickup,
          shippingQuote,shippingProvider:'GHN',shippingEnvironment:q.environment,paymentId:orderId,paymentMode:'SIMULATED',
          orderStatus:'PENDING_PAYMENT',paymentStatus:'PENDING',shippingStatus:'READY',trackingCode:'',paymentDeadline:new Date(Date.now()+15*60*1000),createdAt:now,updatedAt:now});
        tx.create(db.doc(`payments/${orderId}`),{orderId,buyerId:uid,sellerId:group.sellerId,amount:group.price+group.shippingFee,currency:'VND',status:'PENDING',provider:'SIMULATED',createdAt:now,updatedAt:now});
        writeOrderEvent(tx,orderRef,'','PENDING_PAYMENT',uid,'Báo giá GHN đã được xác nhận; thanh toán vẫn mô phỏng.');
        for(const item of group.items){tx.update(db.doc(`posts/${item.postId}`),{status:'RESERVED',reservedForOrderId:orderId,updatedAt:now});tx.delete(db.doc(`users/${uid}/cartItems/${item.postId}`));}
        tx.create(db.doc(`users/${group.sellerId}/notifications/created_${orderId}`),{type:'ORDER_AWAITING_PAYMENT',title:'Đơn hàng mới',body:'Đơn mới có báo giá GHN; thanh toán mô phỏng.',orderId,read:false,createdAt:now});
      }
      tx.create(ref,{postIds:ids,addressId:q.addressId,buyerId:uid,orderIds,createdAt:now});return single?{orderId:orderIds[0]}:{orderIds};
    });
  }
  async function createFree(request) {
    await requireVerifiedAccount(request);const uid=uidOf(request),q=await loadQuote(request);
    if(q.source.items.length!==1||q.source.items[0].type!=='FREE')fail('failed-precondition','Chọn một món tặng.');
    const item=q.source.items[0],id= require('node:crypto').createHash('sha256').update(`${uid}:${item.postId}`).digest('hex'),ref=db.doc(`freeRequests/${id}`);
    await db.runTransaction(async tx=>{const old=await tx.get(ref);await assertQuote(tx,q,uid);
      if(old.exists&&old.get('status')==='ACCEPTED')fail('failed-precondition','Yêu cầu đã được chấp nhận.');
      tx.set(ref,{postId:item.postId,postSnapshot:item,requesterId:uid,ownerId:item.sellerId,note:cleanText(request.data.note,500,'Lời nhắn',false),
        addressId:q.addressId,shippingAddress:q.groups[0].shippingAddress,shippingQuote:{...q.groups[0],environment:q.environment,quoteId:q.id,provider:'GHN'},
        shippingProvider:'GHN',status:'PENDING',createdAt:old.exists&&old.get('status')==='PENDING'?old.get('createdAt'):FieldValue.serverTimestamp(),updatedAt:FieldValue.serverTimestamp()});});
    return {requestId:id};
  }
  async function acceptFree(request) {
    const uid=uidOf(request),id=cleanDocumentId(request.data.requestId,'Yêu cầu'),ref=db.doc(`freeRequests/${id}`),old=await ref.get();
    if(!old.exists||old.get('ownerId')!==uid)fail('permission-denied','Không có quyền xử lý yêu cầu.');
    if(old.get('status')==='ACCEPTED')return {orderId:old.get('orderId')};
    const saved=old.get('shippingQuote');if(!saved)fail('failed-precondition','Người nhận cần xác nhận báo giá GHN mới.');
    // Requester explicitly accepted this fee; recheck inputs and current fee without silently increasing it.
    const q=await loadQuote({auth:{uid:old.get('requesterId')},data:{quoteId:saved.quoteId,addressId:old.get('addressId')}},{allowExpired:true});
    const orderRef=db.collection('orders').doc();
    await db.runTransaction(async tx=>{
      const current=await tx.get(ref),post=await tx.get(db.doc(`posts/${old.get('postId')}`));
      const pending=await tx.get(db.collection('freeRequests').where('postId','==',old.get('postId')).where('status','==','PENDING').limit(450));
      if(current.get('status')!=='PENDING'||hash(current.get('shippingQuote'))!==hash(saved)||!post.exists||post.get('status')!=='ACTIVE'||post.get('reservedForOrderId'))fail('failed-precondition','Yêu cầu hoặc món đã đổi.');
      // Freeze the same address/config/product versions atomically; original five-minute quote expiry is intentionally waived only for this existing free request.
      const group=q.groups[0],address=await tx.get(db.doc(`users/${q.buyerId}/addresses/${q.addressId}`)),settings=await tx.get(db.doc(`shippingConfig/ghn_${q.environment}`)),seller=await tx.get(db.doc(`ghnSellerShops/${q.environment}_${group.sellerId}`));
      if(!address.exists||hash({items:[view(post)],address:address.data(),settings:settings.data(),sellers:{[group.sellerId]:seller.data()}})!==q.sourceHash)fail('failed-precondition','Người nhận cần cập nhật báo giá/địa chỉ.');
      const now=FieldValue.serverTimestamp();
      tx.create(orderRef,{orderType:'FREE',buyerId:q.buyerId,sellerId:uid,postId:post.id,postSnapshot:view(post),requestId:id,
        price:0,shippingFee:group.shippingFee,shippingAddress:group.shippingAddress,pickupAddress:group.pickup,shippingQuote:saved,
        shippingProvider:'GHN',shippingEnvironment:q.environment,paymentMode:'SIMULATED',orderStatus:'CONFIRMED',paymentStatus:'NOT_REQUIRED',shippingStatus:'READY',trackingCode:'',createdAt:now,updatedAt:now});
      tx.update(ref,{status:'ACCEPTED',orderId:orderRef.id,updatedAt:now});tx.update(post.ref,{status:'RESERVED',reservedForOrderId:orderRef.id,updatedAt:now});
      for(const doc of pending.docs)if(doc.id!==id)tx.update(doc.ref,{status:'UNAVAILABLE',updatedAt:now});
      writeOrderEvent(tx,orderRef,'','CONFIRMED',uid,'Đã chọn người nhận; phí GHN do người nhận trả khi giao.');
      for(const recipient of new Set([q.buyerId,...pending.docs.map(d=>d.get('requesterId'))]))tx.create(db.collection(`users/${recipient}/notifications`).doc(),{type:recipient===q.buyerId?'FREE_REQUEST_ACCEPTED':'FREE_REQUEST_UNAVAILABLE',title:'Cập nhật yêu cầu nhận đồ',body:recipient===q.buyerId?'Bạn đã được chọn nhận đồ.':'Món đồ đã có người nhận.',orderId:orderRef.id,requestId:id,read:false,createdAt:now});
    });return {orderId:orderRef.id};
  }
  return {quote,createOrders,createFree,acceptFree,feePayload};
};
