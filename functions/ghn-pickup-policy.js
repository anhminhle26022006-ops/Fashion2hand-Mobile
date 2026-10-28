/** Formatting/contact edits do not change the physical pickup approved by Admin. */
const normalizeStreet=value=>String(value||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[đĐ]/g,'d').toLowerCase().replace(/[.,;:]/g,' ').replace(/\s+/g,' ').trim();
function pickupMappingUpdate(mapping,address) {
  const previous=mapping.pickup||{},a=previous.ghn||{},b=address.ghn||{};
  const valid=Number.isSafeInteger(b.provinceId)&&b.provinceId>0&&Number.isSafeInteger(b.districtId)&&b.districtId>0&&typeof b.wardCode==='string'&&b.wardCode.length>0&&['staging','production'].includes(b.environment);
  const sameCodes=valid&&['provinceId','districtId','wardCode','environment'].every(key=>a[key]===b[key]);
  const approvedStreet=normalizeStreet(mapping.registeredShopAddress||previous.street);
  if(!sameCodes||!approvedStreet||approvedStreet!==normalizeStreet(address.street))return {enabled:false,invalidatedReason:'PICKUP_LOCATION_CHANGED'};
  // An inactive mapping needs an explicit administrative repair; saving an address cannot enable it.
  if(mapping.enabled!==true)return null;
  if(previous.recipient!==address.recipient||previous.phone!==address.phone)return {pickup:{...previous,recipient:address.recipient,phone:address.phone}};
  return null;
}
module.exports={normalizeStreet,pickupMappingUpdate};
