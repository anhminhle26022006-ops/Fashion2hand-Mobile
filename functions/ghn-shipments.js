const { hash, STATUS, terminal, transition, GhnError } = require('./ghn-provider');
const reference = orderId => `F2-${hash(orderId).slice(0,40)}`;

module.exports = ({ db, FieldValue, fail, uidOf, cleanDocumentId, writeOrderEvent, provider }) => {
  function payload(order, code) {
    const q=order.shippingQuote;
    if(!q||q.provider!=='GHN'||!q.pickup?.ghn||!q.shippingAddress?.ghn)fail('failed-precondition','Đơn cũ chưa có báo giá và snapshot địa chỉ GHN; không tự bịa dữ liệu để gửi.');
    const p=provider();if(q.environment!==p.settings.environment)fail('failed-precondition','Đơn và cấu hình GHN thuộc môi trường khác nhau.');
    if(order.orderType==='PAID'&&q.environment==='production')fail('failed-precondition','Chưa tích hợp thanh toán tiền thật/COD cho đơn bán. Không gửi đơn production với thanh toán mô phỏng.');
    if(order.orderType==='PAID'&&order.paymentStatus!=='PAID'||order.orderType==='FREE'&&order.paymentStatus!=='NOT_REQUIRED')fail('failed-precondition','Đơn chưa đủ điều kiện thanh toán để giao.');
    const from=q.pickup,to=q.shippingAddress;
    return {client_order_code:code,from_name:from.recipient,from_phone:from.phone,from_address:from.street,
      from_ward_name:from.ward,from_district_name:from.district,from_province_name:from.province,is_new_from_address:false,
      return_name:from.recipient,return_phone:from.phone,return_address:from.street,return_ward_name:from.ward,return_district_name:from.district,return_province_name:from.province,is_new_return_address:false,
      to_name:to.recipient,to_phone:to.phone,to_address:to.street,to_ward_name:to.ward,to_district_name:to.district,to_province_name:to.province,is_new_to_address:false,
      service_type_id:q.service.service_type_id,service_id:q.service.service_id,weight:q.parcel.weight,length:q.parcel.length,width:q.parcel.width,height:q.parcel.height,
      payment_type_id:order.orderType==='FREE'?2:1,cod_amount:0,cod_failed_amount:0,insurance_value:order.price,order_value:order.price,
      required_note:'CHOXEMHANGKHONGTHU',content:q.items.map(i=>i.title).join(', ').slice(0,2000),items:q.items.map(i=>({name:i.title,code:i.postId,quantity:1,price:i.price}))};
  }
  function validateDetail(detail,op) {
    if(!detail||typeof detail.order_code!=='string'||detail.client_order_code!==op.reference||detail.shop_id!==op.shopId
      ||op.environment!==provider().settings.environment||!STATUS[detail.status])fail('failed-precondition','Kết quả GHN không khớp mã nội bộ, shop hoặc môi trường.');
    if(detail.cod_amount!==0||detail.payment_type_id!==op.payload.payment_type_id)fail('failed-precondition','COD hoặc bên trả phí tại GHN không khớp đơn. Cần đối soát thủ công.');
  }
  async function apply(op,detail) {
    validateDetail(detail,op);
    const orderRef=db.doc(`orders/${op.orderId}`),shipmentRef=db.doc(`shipments/${op.orderId}`),operationRef=db.doc(`ghnOperations/${op.reference}`);
    return db.runTransaction(async tx=>{
      const [order,prior,operation]=await Promise.all([tx.get(orderRef),tx.get(shipmentRef),tx.get(operationRef)]);
      if(!order.exists||!operation.exists||operation.get('environment')!==op.environment||operation.get('shopId')!==op.shopId||operation.get('orderId')!==op.orderId)fail('failed-precondition','Không tìm thấy liên kết vận đơn của backend.');
      if(prior.exists&&prior.get('trackingCode')!==detail.order_code)fail('failed-precondition','Đơn đã được gắn với vận đơn khác.');
      const old=prior.exists?prior.data():{};
      if(!transition(old,detail)){
        tx.set(operationRef,{state:'LINKED',trackingCode:detail.order_code,updatedAt:FieldValue.serverTimestamp()},{merge:true});
        return {trackingCode:detail.order_code,status:old.status,unchanged:true};
      }
      const normal=STATUS[detail.status],now=FieldValue.serverTimestamp(),eventKey=hash([op.environment,detail.order_code,detail.status,detail.updated_date]);
      const eventRef=db.doc(`webhookEvents/ghn_${eventKey}`),event=await tx.get(eventRef);
      if(event.exists)return {trackingCode:detail.order_code,status:normal,unchanged:true};
      const blocked=['CANCELLED','REFUNDED','COMPLETED'].includes(order.get('orderStatus'));
      const returning=['RETURNING','RETURN_FAILED','RETURNED'].includes(normal);
      let next=order.get('orderStatus');
      if(!blocked){
        if(normal==='DELIVERED')next='DELIVERED';
        else if(!['READY','PICKING'].includes(normal)&&!terminal.has(normal))next='SHIPPING';
        // Return/cancellation/exception do not automatically issue money or close a dispute.
      }
      const eventData={status:normal,description:`GHN: ${detail.status}`,createdAt:detail.updated_date};
      const record={orderId:op.orderId,buyerId:order.get('buyerId'),sellerId:order.get('sellerId'),provider:'GHN',carrier:'GHN',environment:op.environment,
        mode:op.environment==='staging'?'STAGING':'LIVE_SHIPPING',trackingCode:detail.order_code,clientOrderCode:op.reference,shopId:op.shopId,
        providerStatus:detail.status,normalizedStatus:normal,status:normal,providerUpdatedAt:detail.updated_date,
        service:op.quote.service,quotedFee:op.quote.shippingFee,providerFee:Number.isSafeInteger(detail.total_fee)?detail.total_fee:null,
        expectedDeliveryTime:detail.leadtime||null,pickupAddress:op.quote.pickup,shippingAddress:op.quote.shippingAddress,parcel:op.quote.parcel,
        updatedAt:now,...(!prior.exists?{createdAt:now}:{}),events:FieldValue.arrayUnion(eventData)};
      tx.set(shipmentRef,record,{merge:true});
      const update={trackingCode:detail.order_code,shippingStatus:normal,orderStatus:next,updatedAt:now,
        ...(returning||['CANCELLED','EXCEPTION','LOST','DAMAGED','SCRAPPED'].includes(normal)?{shippingNeedsReview:true}:{}),
        ...(normal==='DELIVERED'&&!order.get('deliveredAt')?{deliveredAt:now,inspectionDeadline:new Date(Date.now()+3*86400000)}:{})};
      // A returned or terminal exception shipment cannot be settled by the delivery escrow path.
      tx.update(orderRef,update);tx.set(operationRef,{state:'LINKED',trackingCode:detail.order_code,updatedAt:now},{merge:true});
      tx.set(db.doc(`ghnTracking/${op.environment}_${detail.order_code}`),{orderId:op.orderId,reference:op.reference,shopId:op.shopId,environment:op.environment});
      tx.create(eventRef,{provider:'GHN',orderId:op.orderId,status:detail.status,providerUpdatedAt:detail.updated_date,createdAt:now});
      writeOrderEvent(tx,orderRef,order.get('orderStatus'),next,'GHN',`GHN đã xác minh: ${detail.status}${blocked?' · đơn đã đóng, cần kiểm tra nếu có khác biệt':''}`);
      for(const uid of [order.get('buyerId'),order.get('sellerId')])tx.create(db.doc(`users/${uid}/notifications/ghn_${eventKey}`),{
        type:'SHIPMENT_UPDATED',title:'Cập nhật vận chuyển GHN',body:`${detail.order_code} · ${detail.status}${op.environment==='staging'?' · thử nghiệm':''}`,orderId:op.orderId,read:false,createdAt:now});
      return {trackingCode:detail.order_code,status:normal};
    });
  }
  async function reconcile(orderId) {
    const op=await db.doc(`ghnOperations/${reference(orderId)}`).get();
    if(!op.exists)return {pending:false};
    const data=op.data();if(data.environment!==provider().settings.environment)fail('failed-precondition','Không đối soát đơn thuộc môi trường GHN khác.');
    // All retries after a lost response are lookups, never another create request.
    const detail=data.trackingCode?await provider().detail(data.shopId,data.trackingCode):await provider().byReference(data.shopId,data.reference);
    return apply(data,detail);
  }
  async function create(request) {
    const uid=uidOf(request),orderId=cleanDocumentId(request.data.orderId,'Đơn hàng'),ref=reference(orderId),opRef=db.doc(`ghnOperations/${ref}`);
    const preview=await db.doc(`orders/${orderId}`).get();
    if(!preview.exists||preview.get('sellerId')!==uid)fail('permission-denied','Chỉ người bán của đơn được tạo vận đơn.');
    const existing=await opRef.get();if(existing.exists&&existing.get('state')!=='REJECTED')return reconcile(orderId);
    payload(preview.data(),ref);
    const quoted=preview.get('shippingQuote');
    const currentFee=await provider().fee(quoted.shopId,{from_district_id:quoted.pickup.ghn.districtId,from_ward_code:quoted.pickup.ghn.wardCode,
      to_district_id:quoted.shippingAddress.ghn.districtId,to_ward_code:quoted.shippingAddress.ghn.wardCode,service_id:quoted.service.service_id,service_type_id:quoted.service.service_type_id,
      weight:quoted.parcel.weight,length:quoted.parcel.length,width:quoted.parcel.width,height:quoted.parcel.height,insurance_value:preview.get('price')});
    if(currentFee.total!==quoted.shippingFee)fail('failed-precondition','Phí GHN đã thay đổi sau khi đặt đơn. Cần đối soát và người mua xác nhận lại; không tự tăng tiền hoặc tạo vận đơn.');
    const acquired=await db.runTransaction(async tx=>{
      const [order,op]=await Promise.all([tx.get(db.doc(`orders/${orderId}`)),tx.get(opRef)]);
      if(!order.exists||order.get('sellerId')!==uid)fail('permission-denied','Chỉ người bán của đơn được tạo vận đơn.');
      if(op.exists&&op.get('state')!=='REJECTED')return {fresh:false,op:op.data()};
      if(order.get('orderStatus')!=='READY_TO_SHIP')fail('failed-precondition','Đơn chưa sẵn sàng giao.');
      if(hash(order.get('shippingQuote'))!==hash(quoted))fail('failed-precondition','Snapshot vận chuyển đã thay đổi. Làm mới đơn.');
      if(order.get('orderType')==='PAID'){
        if(!order.get('paymentId'))fail('failed-precondition','Đơn thiếu bản ghi thanh toán.');
        const payment=await tx.get(db.doc(`payments/${order.get('paymentId')}`)),received=await tx.get(db.doc(`moneyTransactions/received_${orderId}`));
        if(!payment.exists||payment.get('status')!=='PAID'||payment.get('orderId')!==orderId||!received.exists||received.get('status')!=='HOLDING')fail('failed-precondition','Thiếu xác nhận thanh toán và bút toán giữ tiền của backend.');
      }
      const body=payload(order.data(),ref),q=order.get('shippingQuote'),data={orderId,reference:ref,environment:q.environment,shopId:q.shopId,quote:q,payload:body,state:'CREATING',updatedAt:FieldValue.serverTimestamp()};
      tx.set(opRef,data);return {fresh:true,op:data};
    });
    if(!acquired.fresh)return reconcile(orderId);
    let created;
    try{created=await provider().create(acquired.op.shopId,acquired.op.payload);}
    catch(error){await opRef.set({state:error.uncertain?'UNKNOWN':'REJECTED',updatedAt:FieldValue.serverTimestamp()},{merge:true});throw error;}
    if(typeof created.order_code!=='string'){
      await opRef.set({state:'UNKNOWN',updatedAt:FieldValue.serverTimestamp()},{merge:true});throw new GhnError('unavailable','GHN chưa trả mã vận đơn hợp lệ. Cần làm mới để đối soát.',true);
    }
    try{
      await opRef.set({state:'UNKNOWN',trackingCode:created.order_code,updatedAt:FieldValue.serverTimestamp()},{merge:true});
      const detail=await provider().detail(acquired.op.shopId,created.order_code);return await apply(acquired.op,detail);
    }catch(_){throw new GhnError('unavailable','GHN có thể đã tạo vận đơn nhưng chưa lưu/đối soát xong. Bấm làm mới, không tạo đơn mới.',true);}
  }
  async function refresh(request) {
    const uid=uidOf(request),id=cleanDocumentId(request.data.orderId,'Đơn hàng'),order=await db.doc(`orders/${id}`).get();
    if(!order.exists||![order.get('buyerId'),order.get('sellerId')].includes(uid))fail('permission-denied','Không có quyền xem đơn.');
    return reconcile(id);
  }
  async function cancel(request) {
    const uid=uidOf(request),id=cleanDocumentId(request.data.orderId,'Đơn hàng'),order=await db.doc(`orders/${id}`).get();
    if(!order.exists||order.get('sellerId')!==uid)fail('permission-denied','Chỉ người bán được yêu cầu hủy vận đơn.');
    const opDoc=await db.doc(`ghnOperations/${reference(id)}`).get();if(!opDoc.exists)fail('failed-precondition','Chưa có thao tác tạo GHN.');
    const op=opDoc.data(),detail=await provider().byReference(op.shopId,op.reference);validateDetail(detail,op);
    if(detail.status==='cancel')return apply(op,detail);
    if(!['ready_to_pick','picking','money_collect_picking'].includes(detail.status))fail('failed-precondition','GHN đã lấy hàng; không hỗ trợ hủy tại trạng thái này.');
    // Acknowledge only after a fresh detail lookup confirms cancel, never trust an optimistic local update.
    await provider().cancel(op.shopId,detail.order_code);
    const verified=await provider().detail(op.shopId,detail.order_code);const result=await apply(op,verified);
    if(verified.status!=='cancel')fail('failed-precondition','GHN chưa xác nhận hủy. Làm mới để kiểm tra; chưa đánh dấu vận đơn đã hủy.');
    return result;
  }
  async function callback(req,res) {
    if(req.method!=='POST')return res.status(405).send('POST required');
    const body=req.body;if(!body||typeof body.OrderCode!=='string'||body.OrderCode.length>100||!Number.isSafeInteger(body.ShopID))return res.status(400).send('Invalid payload');
    try{
      const environment=provider().settings.environment;
      const link=await db.doc(`ghnTracking/${environment}_${body.OrderCode.replace(/[^A-Za-z0-9_-]/g,'')}`).get();
      const ref=link.exists?link.get('reference'):body.ClientOrderCode;
      if(typeof ref!=='string'||!/^F2-[a-f0-9]{40}$/.test(ref))return res.status(200).json({ignored:true});
      const op=await db.doc(`ghnOperations/${ref}`).get();
      if(!op.exists||op.get('shopId')!==body.ShopID||op.get('environment')!==environment)return res.status(200).json({ignored:true});
      // No invented HMAC/Bearer requirement: callback is only a hint. Verify status and code using the secret-backed API.
      const detail=await provider().byReference(op.get('shopId'),ref);
      if(detail.order_code!==body.OrderCode)return res.status(200).json({ignored:true});
      await apply(op.data(),detail);return res.status(200).json({ok:true});
    }catch(_){return res.status(503).json({retry:true});}
  }
  return {create,refresh,cancel,callback,reconcile,apply,payload,reference};
};
