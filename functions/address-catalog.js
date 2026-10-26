const fs = require("node:fs");
const provinces = JSON.parse(fs.readFileSync(require.resolve("./data/vietnam-addresses.json"), "utf8").replace(/^\uFEFF/, ""));
const legacy = JSON.parse(fs.readFileSync(require.resolve('./data/vietnam-addresses-legacy.json'),'utf8'));
function canonicalAddress(source, fail) {
  if(source.addressVersion==='VN_3_LEVEL_LEGACY') {
    const province=legacy.find(p=>String(p.code)===String(source.provinceCode));
    const district=province?.districts.find(d=>String(d.code)===String(source.districtCode));
    const ward=district?.wards.find(w=>String(w.code)===String(source.wardCode));
    const wardless=district?.wards.length===0&&!source.wardCode&&!source.ward;
    if(!province||!district||(!ward&&!wardless))fail('invalid-argument','Chọn đúng tỉnh/thành phố, quận/huyện và phường/xã trong danh mục 63 tỉnh/thành trước sáp nhập.');
    return {province:province.name,provinceCode:String(province.code),district:district.name,districtCode:String(district.code),ward:ward?.name||'',wardCode:ward?String(ward.code):'',addressVersion:'VN_3_LEVEL_LEGACY'};
  }
  if(source.addressVersion&&source.addressVersion!=='VN_2_LEVEL_2025')fail('invalid-argument','Phiên bản danh mục địa chỉ không được hỗ trợ.');
  const province = provinces.find(p => String(p.code) === String(source.provinceCode));
  const ward = province?.wards.find(w => String(w.code) === String(source.wardCode));
  if (!province || !ward) fail("invalid-argument", "Chọn tỉnh/thành phố và phường/xã trong danh sách hiện hành.");
  return { province: province.name, provinceCode: String(province.code), ward: ward.name, wardCode: String(ward.code), district: "", addressVersion: "VN_2_LEVEL_2025" };
}
module.exports = { canonicalAddress };
