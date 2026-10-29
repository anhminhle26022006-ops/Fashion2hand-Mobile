const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {canonicalAddress}=require('../address-catalog');
const legacy=require('../data/vietnam-addresses-legacy.json');
const fail=(code,message)=>{const e=new Error(message);e.code=code;throw e;};
const hcm=legacy.find(p=>p.code===79),district=hcm.districts.find(d=>d.name==='Quận 8'),ward=district.wards.find(w=>w.name==='Phường 5');
const input={addressVersion:'VN_3_LEVEL_LEGACY',provinceCode:String(hcm.code),districtCode:String(district.code),wardCode:String(ward.code),province:'FORGED',district:'FORGED',ward:'FORGED'};
test('all 63 provinces, 696 districts and 10051 wards match bundled Android directory',()=>{
 assert.equal(legacy.length,63);const districts=legacy.flatMap(p=>p.districts),wards=districts.flatMap(d=>d.wards);
 assert.equal(districts.length,696);assert.equal(wards.length,10051);
 assert.equal(new Set(districts.map(d=>d.code)).size,696);assert.equal(new Set(wards.map(w=>w.code)).size,10051);
 const expected=legacy.flatMap(p=>p.districts.flatMap(d=>d.wards.length?d.wards.map(w=>[p.code,p.name,d.code,d.name,w.code,w.name].join('\t')):[[p.code,p.name,d.code,d.name,'',''].join('\t')]));
 assert.deepEqual(fs.readFileSync(require('node:path').resolve(__dirname,'../../app/src/main/assets/vietnam-addresses-legacy.tsv'),'utf8').trimEnd().split('\n').map(s=>s.replace(/\r$/,'')),expected);
});
test('legacy codes canonicalize to correct Quận 8 / Phường 5 without trusting names',()=>{
 const actual=canonicalAddress(input,fail);assert.equal(actual.province,'Thành phố Hồ Chí Minh');assert.equal(actual.district,'Quận 8');assert.equal(actual.ward,'Phường 5');assert.equal(actual.addressVersion,'VN_3_LEVEL_LEGACY');
});
test('district from another province, ward from another district and missing parent rejected',()=>{
 const other=hcm.districts.find(d=>d.code!==district.code&&d.wards.length);
 for(const data of [{...input,provinceCode:'1'},{...input,wardCode:String(other.wards[0].code)},{...input,districtCode:''},{...input,addressVersion:'UNKNOWN'}])assert.throws(()=>canonicalAddress(data,fail),e=>e.code==='invalid-argument');
});
test('existing 34-province addresses remain compatible and do not infer a legacy district',()=>{
 const current=require('../data/vietnam-addresses.json'),p=current.find(p=>p.code===79),w=p.wards.find(w=>w.name==='Phường Sài Gòn');
 const actual=canonicalAddress({provinceCode:p.code,wardCode:w.code,addressVersion:'VN_2_LEVEL_2025'},fail);
 assert.equal(actual.ward,'Phường Sài Gòn');assert.equal(actual.district,'');assert.equal(actual.addressVersion,'VN_2_LEVEL_2025');
});
test('wardless island districts are represented without fabricated ward or shipping codes',()=>{
 const p=legacy.find(p=>p.districts.some(d=>d.wards.length===0)),d=p.districts.find(d=>!d.wards.length);
 const actual=canonicalAddress({addressVersion:'VN_3_LEVEL_LEGACY',provinceCode:p.code,districtCode:d.code,wardCode:'',ward:''},fail);
 assert.equal(actual.ward,'');assert.equal(actual.wardCode,'');assert.equal(actual.district,d.name);assert.equal(actual.ghn,undefined);
 assert.throws(()=>canonicalAddress({...input,districtCode:d.code,provinceCode:p.code},fail));
});
