/** Acceptance commits first. Provider uncertainty never rolls it back or creates a second order. */
module.exports=({checkout,shipment,db,FieldValue})=>async request=>{
  const result=await checkout.acceptFree(request);
  const ref=db.doc(`orders/${result.orderId}`),order=await ref.get();
  // Older accepted orders did not authorize automatic pickup; preserve their existing flow.
  const stagingQuote=order.get('shippingEnvironment')==='staging'&&order.get('shippingAuthorization.source')==='STAGING_SERVER_QUOTE';
  if(order.get('shippingConsent.accepted')!==true&&!stagingQuote)return {...result,shipmentPending:true};
  try {
    const shipping=await shipment.create({...request,data:{orderId:result.orderId}});
    await ref.update({autoShippingStatus:'CREATED',updatedAt:FieldValue.serverTimestamp()});
    return {...result,shipping,shipmentPending:false};
  } catch(error) {
    await ref.update({autoShippingStatus:'RETRY_REQUIRED',updatedAt:FieldValue.serverTimestamp()});
    return {...result,shipmentPending:true};
  }
};
