// Run deliberately when updating the bundled administrative directory.
const fs = require('node:fs/promises');
const path = require('node:path');
async function main() {
  if (process.argv.includes('--legacy')) {
    const root=path.resolve(__dirname,'..'),cache=path.join(root,'.gradle/vietnam-addresses-legacy-source.json');
    const provinces=process.argv.includes('--from-cache')?JSON.parse(await fs.readFile(cache,'utf8')):
      await fetch('https://provinces.open-api.vn/api/v1/?depth=3').then(r=>{if(!r.ok)throw new Error('Legacy directory download failed');return r.json();});
    if(!Array.isArray(provinces)||provinces.length!==63)throw new Error('Expected 63 legacy provinces');
    const provinceCodes=new Set(),districtCodes=new Set(),wardCodes=new Set(),rows=[];
    for(const p of provinces){
      if(!p.code||!p.name||provinceCodes.has(p.code)||!Array.isArray(p.districts)||!p.districts.length)throw new Error('Invalid legacy province');
      provinceCodes.add(p.code);
      for(const d of p.districts){
        if(!d.code||!d.name||districtCodes.has(d.code)||d.province_code!==p.code||!Array.isArray(d.wards))throw new Error('Invalid legacy district');
        districtCodes.add(d.code);
        if(!d.wards.length)rows.push([p.code,p.name,d.code,d.name,'',''].join('\t'));
        for(const w of d.wards){
          if(!w.code||!w.name||wardCodes.has(w.code)||w.district_code!==d.code)throw new Error('Invalid legacy ward');
          const fields=[p.code,p.name,d.code,d.name,w.code,w.name].map(String);
          if(fields.some(v=>/[\t\r\n]/.test(v)))throw new Error('Invalid directory text');
          wardCodes.add(w.code);rows.push(fields.join('\t'));
        }
      }
    }
    await fs.writeFile(path.join(root,'functions/data/vietnam-addresses-legacy.json'),JSON.stringify(provinces,null,2)+'\n');
    await fs.writeFile(path.join(root,'app/src/main/assets/vietnam-addresses-legacy.tsv'),rows.join('\n')+'\n');
    console.log(`Legacy directory: ${provinceCodes.size} provinces, ${districtCodes.size} districts, ${wardCodes.size} wards.`);
    return;
  }
  const response = await fetch('https://provinces.open-api.vn/api/v2/?depth=2');
  if (!response.ok) throw new Error(`Directory HTTP ${response.status}`);
  const provinces = await response.json();
  if (!Array.isArray(provinces) || provinces.length !== 34) throw new Error('Review changed province count before updating');
  const codes = new Set(), rows = [];
  for (const province of provinces) {
    if (!province.code || !province.name || !Array.isArray(province.wards) || !province.wards.length) throw new Error('Invalid province');
    for (const ward of province.wards) {
      if (!ward.code || !ward.name || codes.has(ward.code)) throw new Error('Invalid or duplicate ward');
      const fields = [province.code, province.name, ward.code, ward.name].map(String);
      if (fields.some(value => /[\t\r\n]/.test(value))) throw new Error('Invalid directory text');
      codes.add(ward.code); rows.push(fields.join('\t'));
    }
  }
  if (rows.length !== 3321) throw new Error('Review changed ward count before updating');
  const root = path.resolve(__dirname, '..');
  await fs.writeFile(path.join(root,'functions/data/vietnam-addresses.json'), JSON.stringify(provinces,null,2)+'\n');
  await fs.writeFile(path.join(root,'app/src/main/assets/vietnam-addresses.tsv'), rows.join('\n')+'\n');
  console.log(`Updated ${provinces.length} provinces and ${rows.length} wards; update ADDRESS_DATA.md date and run address tests.`);
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
