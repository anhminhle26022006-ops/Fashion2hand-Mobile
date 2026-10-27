const {test}=require('node:test');
const assert=require('node:assert/strict');
const {normalizeStreet,pickupMappingUpdate}=require('../ghn-pickup-policy');
const address=()=>({recipient:'Seller',phone:'0901234567',street:'222 Ta Quang Buu',ghn:{provinceId:202,districtId:1450,wardCode:'20804',environment:'staging'}});
const mapping=()=>({enabled:true,registeredShopAddress:'222 Tạ Quang Bửu',pickup:{...address(),street:'222 Tạ Quang Bửu'}});
test('accents, case, spaces and punctuation keep the approved pickup active',()=>{
 assert.equal(pickupMappingUpdate(mapping(),{...address(),street:' 222 TẠ QUANG BỬU. '}),null);
 assert.equal(pickupMappingUpdate(mapping(),address()),null);
});
test('phone and contact edits update pickup contact without disabling or changing registered street',()=>{
 const updated=pickupMappingUpdate(mapping(),{...address(),recipient:'Updated seller',phone:'0912345678'});
 assert.equal(updated.enabled,undefined);assert.equal(updated.pickup.recipient,'Updated seller');assert.equal(updated.pickup.phone,'0912345678');assert.equal(updated.pickup.street,'222 Tạ Quang Bửu');
});
test('different street, house/unit separator, ward or environment still invalidates mapping',()=>{
 assert.equal(pickupMappingUpdate(mapping(),{...address(),street:'223 Ta Quang Buu'}).enabled,false);
 assert.notEqual(normalizeStreet('22/2 Tạ Quang Bửu'),normalizeStreet('222 Ta Quang Buu'));
 for(const ghn of [{...address().ghn,wardCode:'other'},{...address().ghn,environment:'production'}])assert.equal(pickupMappingUpdate(mapping(),{...address(),ghn}).enabled,false);
});
test('address save never enables an inactive mapping or accepts missing GHN identifiers',()=>{
 assert.equal(pickupMappingUpdate({...mapping(),enabled:false},address()),null);
 assert.equal(pickupMappingUpdate(mapping(),{...address(),ghn:{}}).enabled,false);
});
