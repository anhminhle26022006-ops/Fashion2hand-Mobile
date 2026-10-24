// Run deliberately when updating the bundled administrative directory.
const fs = require('node:fs/promises');
const path = require('node:path');
async function main() {
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
