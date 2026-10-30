const crypto = require('node:crypto');

const BASES = Object.freeze({ staging: 'https://dev-online-gateway.ghn.vn/shiip/public-api', production: 'https://online-gateway.ghn.vn/shiip/public-api' });
class GhnError extends Error {
  constructor(code, message, uncertain = false, details) { super(message); this.code = code; this.uncertain = uncertain; this.details = details; }
}
// Firestore and client JSON may enumerate object fields differently. Fingerprints
// must depend on values, not insertion order (especially groups from many sellers).
const hash = value => crypto.createHash('sha256').update(JSON.stringify(value, (_, item) =>
  item && typeof item === 'object' && !Array.isArray(item)
    ? Object.fromEntries(Object.keys(item).sort().map(key => [key,item[key]])) : item)).digest('hex');
function config(env = process.env) {
  if (!BASES[env.GHN_ENV]) throw new GhnError('failed-precondition', 'Chọn GHN_ENV=staging hoặc production; không tự chọn môi trường.', false, {reason:'GHN_ENV_INVALID'});
  if (env.GHN_TOKEN_ENV !== env.GHN_ENV) throw new GhnError('failed-precondition', 'GHN_TOKEN_ENV phải khớp GHN_ENV. Token và Shop ID phải thuộc cùng môi trường.', false, {reason:'GHN_TOKEN_ENV_MISMATCH'});
  return { environment: env.GHN_ENV, base: BASES[env.GHN_ENV], productionEnabled: env.GHN_PRODUCTION_ENABLED === 'true' };
}
const STATUS = Object.freeze({
  ready_to_pick: 'READY', picking: 'PICKING', money_collect_picking: 'PICKING',
  picked: 'IN_TRANSIT', storing: 'IN_TRANSIT', sorting: 'IN_TRANSIT', transporting: 'IN_TRANSIT',
  delivering: 'IN_TRANSIT', money_collect_delivering: 'IN_TRANSIT', delivered: 'DELIVERED',
  delivery_fail: 'DELIVERY_FAILED', waiting_to_return: 'RETURNING', return: 'RETURNING',
  return_transporting: 'RETURNING', return_sorting: 'RETURNING', returning: 'RETURNING', return_fail: 'RETURN_FAILED',
  returned: 'RETURNED', cancel: 'CANCELLED', exception: 'EXCEPTION', lost: 'LOST', damage: 'DAMAGED', scrap: 'SCRAPPED'
});
const terminal = new Set(['DELIVERED', 'RETURNED', 'CANCELLED', 'EXCEPTION', 'LOST', 'DAMAGED', 'SCRAPPED']);
function transition(prior, incoming) {
  if (!STATUS[incoming.status]) throw new GhnError('failed-precondition', 'Trạng thái GHN chưa được hỗ trợ; cần đối soát.');
  const at = Date.parse(incoming.updated_date);
  if (!Number.isFinite(at)) throw new GhnError('failed-precondition', 'GHN thiếu thời điểm trạng thái, không tự suy đoán thứ tự.');
  const oldAt = Date.parse(prior.providerUpdatedAt || '');
  if (Number.isFinite(oldAt) && at < oldAt) return false;
  if (prior.providerStatus === incoming.status && Number.isFinite(oldAt) && at <= oldAt) return false;
  if (terminal.has(prior.normalizedStatus) && prior.normalizedStatus !== STATUS[incoming.status]) return false;
  if (Number.isFinite(oldAt) && at === oldAt && prior.providerStatus && prior.providerStatus !== incoming.status) return false;
  return true;
}
function parcel(items, estimate) {
  if (!estimate || estimate.estimated !== true) throw new GhnError('failed-precondition', 'Chưa cấu hình ước tính kiện hàng phía server.');
  for (const key of ['weight', 'length', 'width', 'height']) if (!Number.isSafeInteger(estimate[key]) || estimate[key] <= 0)
    throw new GhnError('failed-precondition', 'Thông số kiện hàng phải là số nguyên dương: gram và cm.');
  const result = { weight: estimate.weight * items.length, length: estimate.length, width: estimate.width, height: estimate.height * items.length, estimated: true };
  if (!items.length || result.weight >= 20000 || Math.max(result.length, result.width, result.height) > 200)
    throw new GhnError('failed-precondition', 'Tích hợp hiện hỗ trợ một kiện dưới 20kg, mỗi chiều tối đa 200cm. Cần cấu hình kiện phù hợp.');
  return result;
}
class GhnProvider {
  constructor({ token, env = process.env, fetchImpl = fetch, timeoutMs = 12000, log = () => {} }) {
    this.settings = config(env); this.token = token; this.fetch = fetchImpl; this.timeoutMs = timeoutMs; this.log = log;
  }
  async request(path, data = {}, { shopId, method = 'POST', mutation = false } = {}) {
    if (mutation && this.settings.environment === 'production' && !this.settings.productionEnabled)
      throw new GhnError('failed-precondition', 'Chưa cho phép thao tác vận chuyển production.');
    const token = typeof this.token === 'function' ? this.token() : this.token;
    if (typeof token !== 'string' || !token.trim()) throw new GhnError('failed-precondition', 'Chưa cấu hình Firebase Secret GHN_TOKEN.', false, {reason:'GHN_TOKEN_MISSING',environment:this.settings.environment});
    if (shopId != null && (!Number.isSafeInteger(shopId) || shopId <= 0)) throw new GhnError('failed-precondition', 'Shop ID GHN chưa hợp lệ.');
    const url = new URL(this.settings.base + path);
    if (method === 'GET') for (const [key, value] of Object.entries(data)) url.searchParams.set(key, String(value));
    let response, body;
    try {
      response = await this.fetch(url, { method, redirect: 'error', headers: { Token: token, 'Content-Type': 'application/json', ...(shopId ? { ShopId: String(shopId) } : {}) },
        ...(method === 'GET' ? {} : { body: JSON.stringify(data) }), signal: AbortSignal.timeout(this.timeoutMs) });
      body = await response.json();
    } catch (_) {
      this.log({ provider: 'GHN', operation: path, outcome: mutation ? 'UNKNOWN' : 'UNAVAILABLE' });
      throw new GhnError('unavailable', mutation ? 'Chưa xác định kết quả GHN. Không tạo lại; hãy làm mới để đối soát theo mã đơn nội bộ.' : 'Không kết nối được GHN. Vui lòng thử lại.', mutation);
    }
    if (!response.ok || body?.code !== 200 || body.data == null) {
      // Never propagate provider text: it may echo headers, addresses, phone numbers or request bodies.
      this.log({ provider: 'GHN', operation: path, status: response.status, outcome: response.status >= 500 ? 'UNKNOWN' : 'REJECTED' });
      const uncertain = mutation && (response.status >= 500 || response.status === 408 || Number(body?.code) >= 500);
      throw new GhnError(response.status === 401 || response.status === 403 ? 'failed-precondition' : 'unavailable',
        uncertain ? 'GHN chưa xác nhận kết quả. Cần đối soát, không tự gửi lại lệnh tạo.' : 'GHN từ chối yêu cầu. Kiểm tra Token, Shop ID, địa chỉ, tuyến và dịch vụ.', uncertain,
        {reason:[401,403].includes(response.status)?'GHN_AUTH_REJECTED':'GHN_REQUEST_REJECTED',environment:this.settings.environment,providerHttpStatus:response.status,...(path==='/v2/shop/all'?{operation:'SHOPS_LIST'}:{})});
    }
    return body.data;
  }
  shops(offset = 0) { return this.request('/v2/shop/all', { offset, limit: 50 }, { method: 'GET' }); }
  registerShop(shopId, body) { return this.request('/v2/shop/register', body, { shopId, mutation: true }); }
  provinces(shopId) { return this.request('/master-data/province', {}, { shopId, method: 'GET' }); }
  districts(shopId, provinceId) { return this.request('/master-data/district', { province_id: provinceId }, { shopId, method: 'GET' }); }
  wards(shopId, districtId) { return this.request('/master-data/ward', { district_id: districtId }, { shopId, method: 'GET' }); }
  services(shopId, from, to) { return this.request('/v2/shipping-order/available-services', { shop_id: shopId, from_district: from, to_district: to }, { shopId }); }
  fee(shopId, body) { return this.request('/v2/shipping-order/fee', body, { shopId }); }
  create(shopId, body) { return this.request('/v2/shipping-order/create', body, { shopId, mutation: true }); }
  detail(shopId, orderCode) { return this.request('/v2/shipping-order/detail', { order_code: orderCode }, { shopId, method: 'GET' }); }
  byReference(shopId, reference) { return this.request('/v2/shipping-order/detail-by-client-code', { client_order_code: reference }, { shopId, method: 'GET' }); }
  cancel(shopId, orderCode) { return this.request('/v2/switch-status/cancel', { order_codes: [orderCode] }, { shopId, mutation: true }); }
  async address(shopId, source) {
    const selected = source?.ghn;
    if (selected && selected.environment !== this.settings.environment)
      throw new GhnError('failed-precondition', 'Địa chỉ GHN thuộc môi trường khác. Chọn lại địa chỉ GHN cho môi trường hiện tại.');
    if (!selected || !Number.isSafeInteger(selected.provinceId) || !Number.isSafeInteger(selected.districtId) || typeof selected.wardCode !== 'string')
      throw new GhnError('failed-precondition', 'Địa chỉ chưa có mã GHN. Chọn địa chỉ giao hàng GHN trong ứng dụng.');
    const province = (await this.provinces(shopId)).find(p => p.ProvinceID === selected.provinceId);
    const district = province && (await this.districts(shopId, selected.provinceId)).find(d => d.DistrictID === selected.districtId);
    const ward = district && (await this.wards(shopId, selected.districtId)).find(w => w.WardCode === selected.wardCode);
    if (!ward) throw new GhnError('failed-precondition', 'Mã GHN không thuộc đúng tỉnh/quận/phường. Chọn lại danh mục GHN.');
    for (const field of ['recipient', 'phone', 'street']) if (typeof source[field] !== 'string' || !source[field].trim()) throw new GhnError('failed-precondition', 'Địa chỉ GHN thiếu người nhận, điện thoại hoặc số nhà/đường.');
    if (!/^\+?[0-9 ()-]{9,20}$/.test(source.phone)) throw new GhnError('failed-precondition', 'Điện thoại nhận hàng không hợp lệ.');
    return { recipient: source.recipient.slice(0,100), phone: source.phone, street: source.street.slice(0,300),
      province: province.ProvinceName, district: district.DistrictName, ward: ward.WardName,
      ghn: { provinceId: selected.provinceId, districtId: selected.districtId, wardCode: selected.wardCode, environment: this.settings.environment, addressModel: 'GHN_LEGACY' } };
  }
}
module.exports = { GhnProvider, GhnError, config, STATUS, terminal, transition, parcel, hash };
