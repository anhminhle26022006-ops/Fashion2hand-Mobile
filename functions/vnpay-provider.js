'use strict';
const crypto = require('node:crypto');
const {isIP} = require('node:net');
const PAYMENT_URL = 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html';
const QUERY_URL = 'https://sandbox.vnpayment.vn/merchant_webapi/api/transaction';
const RETURN_URL = 'https://us-central1-fashion2hand.cloudfunctions.net/vnpayReturn';
class VnpayError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}
function settings(env = process.env) {
  if (env.PAYMENT_PROVIDER !== 'VNPAY' || env.VNPAY_ENV !== 'sandbox')
    throw new VnpayError('failed-precondition', 'Cần PAYMENT_PROVIDER=VNPAY và VNPAY_ENV=sandbox.');
  if (env.VNPAY_PAYMENT_URL !== PAYMENT_URL || env.VNPAY_RETURN_URL !== RETURN_URL)
    throw new VnpayError('failed-precondition', 'VNPAY_PAYMENT_URL/VNPAY_RETURN_URL chưa đúng endpoint sandbox của project.');
  return {environment:'sandbox', paymentUrl:PAYMENT_URL, returnUrl:RETURN_URL};
}
function amountUnits(vnd) {
  if (!Number.isSafeInteger(vnd) || vnd < 5000 || vnd > 9999999999 || !Number.isSafeInteger(vnd * 100))
    throw new VnpayError('failed-precondition', 'Số tiền VNPAY phải là VND nguyên, từ 5.000 đến 9.999.999.999đ.');
  return String(vnd * 100);
}
function dateVn(date) {
  return new Date(date.getTime()+7*3600000).toISOString().slice(0,19).replace(/[-:T]/g,'');
}
const encode = text => encodeURIComponent(text).replace(/%20/g,'+');
function canonical(fields) {
  return Object.keys(fields).filter(k=>!['vnp_SecureHash','vnp_SecureHashType'].includes(k)).sort().map(k=>{
    if (!/^vnp_[A-Za-z0-9]+$/.test(k) || typeof fields[k] !== 'string' || fields[k].length>2048)
      throw new VnpayError('invalid-argument','Tham số VNPAY không hợp lệ.');
    return `${encode(k)}=${encode(fields[k])}`;
  }).join('&');
}
const hmac = (secret,data) => crypto.createHmac('sha512',secret).update(data,'utf8').digest('hex');
function sameHash(received,expected) {
  return typeof received==='string' && /^[a-fA-F0-9]{128}$/.test(received) &&
    crypto.timingSafeEqual(Buffer.from(received,'hex'),Buffer.from(expected,'hex'));
}
function parseQuery(rawUrl) {
  if (typeof rawUrl!=='string' || rawUrl.length>12000) throw new VnpayError('invalid-argument','Invalid callback');
  const fields=Object.create(null),raw=rawUrl.split('?')[1]||'';
  if (/%(?![0-9a-fA-F]{2})/.test(raw)) throw new VnpayError('invalid-argument','Invalid encoding');
  for(const [k,v] of new URLSearchParams(raw)) {
    if (!/^vnp_[A-Za-z0-9]+$/.test(k) || Object.hasOwn(fields,k) || v.includes('\uFFFD') || v.length>2048)
      throw new VnpayError('invalid-argument','Duplicate or invalid callback parameter');
    fields[k]=v;
  }
  return fields;
}
// Direct Cloud Functions/Cloud Run ingress only. Trust the platform-appended
// rightmost XFF hop, never the user-controlled leftmost prefix or client payload.
function clientIp(request, env=process.env) {
  let ip;
  if(env.FUNCTIONS_EMULATOR==='true') ip=request?.socket?.remoteAddress;
  else if(env.K_SERVICE) {
    const xff=request?.headers?.['x-forwarded-for'];
    if(typeof xff==='string')ip=xff.split(',').at(-1).trim();
  }
  if(ip?.startsWith('::ffff:'))ip=ip.slice(7);
  if(!isIP(ip||''))throw new VnpayError('failed-precondition','Không xác định được IP từ ingress tin cậy. Dùng endpoint Functions trực tiếp.');
  return ip;
}
const QUERY_REQUEST_FIELDS=['vnp_RequestId','vnp_Version','vnp_Command','vnp_TmnCode','vnp_TxnRef','vnp_TransactionDate','vnp_CreateDate','vnp_IpAddr','vnp_OrderInfo'];
const QUERY_RESPONSE_FIELDS=['vnp_ResponseId','vnp_Command','vnp_ResponseCode','vnp_Message','vnp_TmnCode','vnp_TxnRef','vnp_Amount','vnp_BankCode','vnp_PayDate','vnp_TransactionNo','vnp_TransactionType','vnp_TransactionStatus','vnp_OrderInfo','vnp_PromotionCode','vnp_PromotionAmount'];
function pipeData(fields,names) {
  return names.map(k=>{
    const value=fields[k]??'';
    if(typeof value!=='string'||value.includes('|')||value.length>2048)throw new VnpayError('invalid-argument','Invalid QueryDR field');
    return value;
  }).join('|');
}
class VnpayProvider {
  constructor({tmnCode,secret,env=process.env,fetchImpl=fetch}) {
    this.settings=settings(env);this.env=env;this.fetch=fetchImpl;
    if(typeof tmnCode!=='string'||!/^[A-Za-z0-9]{8}$/.test(tmnCode)||typeof secret!=='string'||!secret.length)
      throw new VnpayError('failed-precondition','Thiếu Firebase Secrets VNPAY_TMN_CODE/VNPAY_HASH_SECRET hợp lệ.');
    this.tmnCode=tmnCode;this.secret=secret;
  }
  paymentUrl(attempt) {
    const params={vnp_Version:'2.1.0',vnp_Command:'pay',vnp_TmnCode:this.tmnCode,vnp_Amount:amountUnits(attempt.amount),vnp_CurrCode:'VND',
      vnp_TxnRef:attempt.reference,vnp_OrderInfo:`Thanh toan Fashion2hand ${attempt.reference}`,vnp_OrderType:'other',vnp_Locale:'vn',
      vnp_ReturnUrl:this.settings.returnUrl,vnp_IpAddr:attempt.ip,vnp_CreateDate:attempt.createDate,vnp_ExpireDate:attempt.expireDate};
    const data=canonical(params);return `${this.settings.paymentUrl}?${data}&vnp_SecureHash=${hmac(this.secret,data)}`;
  }
  verify(fields) { return sameHash(fields.vnp_SecureHash,hmac(this.secret,canonical(fields))); }
  normalize(fields,{query=false}={}) {
    if(fields.vnp_CurrCode!=null&&fields.vnp_CurrCode!=='VND')throw new VnpayError('invalid-argument','Invalid currency');
    if(fields.vnp_TmnCode!==this.tmnCode||!/^[A-Za-z0-9]{1,100}$/.test(fields.vnp_TxnRef||'')||
      !/^[0-9]{1,12}$/.test(fields.vnp_Amount||'')||!Number.isSafeInteger(Number(fields.vnp_Amount))||Number(fields.vnp_Amount)<=0||
      !/^\d{2}$/.test(fields.vnp_ResponseCode||'')||!/^\d{2}$/.test(fields.vnp_TransactionStatus||'')||
      !/^\d{1,15}$/.test(fields.vnp_TransactionNo||''))throw new VnpayError('invalid-argument','Invalid signed payment data');
    const success=fields.vnp_ResponseCode==='00'&&fields.vnp_TransactionStatus==='00';
    if(success&&(!/^[1-9]\d{0,14}$/.test(fields.vnp_TransactionNo)||!/^\d{14}$/.test(fields.vnp_PayDate||'')))
      throw new VnpayError('invalid-argument','Missing payment evidence');
    return {reference:fields.vnp_TxnRef,units:fields.vnp_Amount,transactionNo:fields.vnp_TransactionNo,
      responseCode:fields.vnp_ResponseCode,transactionStatus:fields.vnp_TransactionStatus,payDate:fields.vnp_PayDate||'',success,
      failed:fields.vnp_TransactionStatus==='02',source:query?'QUERYDR':'IPN'};
  }
  async query(attempt) {
    // Cloud Run uses dynamic egress unless a fixed NAT has been configured.
    // Discover the public server IP for sandbox QueryDR; never use the mobile IP.
    let ip=this.env.VNPAY_QUERY_IP;
    if(!ip&&this.env.K_SERVICE){
      try{
        const response=await this.fetch('https://api.ipify.org',{redirect:'error',signal:AbortSignal.timeout(4000)});
        if(!response.ok)throw new Error('IP lookup failed');
        ip=(await response.text()).trim();
      }catch{throw new VnpayError('unavailable','Chưa thể kiểm tra kết quả thanh toán. Vui lòng thử lại.');}
    }
    if(!isIP(ip||''))throw new VnpayError('failed-precondition','Cấu hình VNPAY_QUERY_IP bằng IP outbound thực tế của server để QueryDR.');
    const body={vnp_RequestId:crypto.randomBytes(16).toString('hex'),vnp_Version:'2.1.0',vnp_Command:'querydr',vnp_TmnCode:this.tmnCode,
      vnp_TxnRef:attempt.reference,vnp_TransactionDate:attempt.createDate,vnp_CreateDate:dateVn(new Date()),vnp_IpAddr:ip,vnp_OrderInfo:`Query ${attempt.reference}`};
    body.vnp_SecureHash=hmac(this.secret,pipeData(body,QUERY_REQUEST_FIELDS));
    let response,data;
    try {response=await this.fetch(QUERY_URL,{method:'POST',redirect:'error',headers:{'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(12000)});data=await response.json();}
    catch(error){throw new VnpayError(error.name==='TimeoutError'?'deadline-exceeded':'unavailable','QueryDR chưa có kết quả; không suy luận thanh toán thất bại.');}
    if(!response.ok)throw new VnpayError('unavailable','QueryDR HTTP không thành công.');
    if(!sameHash(data?.vnp_SecureHash,hmac(this.secret,pipeData(data,QUERY_RESPONSE_FIELDS))))throw new VnpayError('data-loss','Chữ ký QueryDR không hợp lệ.');
    if(data.vnp_TmnCode!==this.tmnCode||data.vnp_TxnRef!==attempt.reference||data.vnp_Command!=='querydr'||!/^[A-Za-z0-9]{1,32}$/.test(data.vnp_ResponseId||''))
      throw new VnpayError('data-loss','QueryDR không khớp giao dịch.');
    if(data.vnp_ResponseCode!=='00')return {pending:true};
    if(data.vnp_TransactionType!=='01')throw new VnpayError('data-loss','QueryDR không phải giao dịch PAY.');
    return this.normalize(data,{query:true});
  }
}
module.exports={VnpayProvider,VnpayError,settings,amountUnits,dateVn,canonical,hmac,sameHash,parseQuery,clientIp,pipeData,QUERY_REQUEST_FIELDS,QUERY_RESPONSE_FIELDS,PAYMENT_URL,RETURN_URL};
