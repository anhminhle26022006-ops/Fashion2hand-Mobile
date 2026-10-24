const fs = require("node:fs");
const provinces = JSON.parse(fs.readFileSync(require.resolve("./data/vietnam-addresses.json"), "utf8").replace(/^\uFEFF/, ""));
function canonicalAddress(source, fail) {
  const province = provinces.find(p => String(p.code) === String(source.provinceCode));
  const ward = province?.wards.find(w => String(w.code) === String(source.wardCode));
  if (!province || !ward) fail("invalid-argument", "Chọn tỉnh/thành phố và phường/xã trong danh sách hiện hành.");
  return { province: province.name, provinceCode: String(province.code), ward: ward.name, wardCode: String(ward.code), district: "", addressVersion: "VN_2_LEVEL_2025" };
}
module.exports = { canonicalAddress };
