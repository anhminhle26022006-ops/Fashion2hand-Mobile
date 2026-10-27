const {test}=require('node:test');
const assert=require('node:assert/strict');
const crypto=require('node:crypto');
const {VnpayProvider,settings,amountUnits,dateVn,canonical,hmac,parseQuery,clientIp,pipeData,QUERY_REQUEST_FIELDS,QUERY_RESPONSE_FIELDS,PAYMENT_URL,RETURN_URL}=require('../vnpay-provider');
const env={PAYMENT_PROVIDER:'VNPAY',VNPAY_ENV:'sandbox',VNPAY_PAYMENT_URL:PAYMENT_URL,VNPAY_RETURN_URL:RETURN_URL,VNPAY_QUERY_IP:'203.0.113.5'};
const fixture={tmnCode:'TESTONLY',secret:'unit-test-secret-only',env};
test('official PAY sample ordering and encoding; golden HMAC independently calculated in Python',()=>{
 const fields={vnp_TxnRef:'5',vnp_Version:'2.1.0',vnp_TmnCode:'DEMOV210',vnp_Amount:'1806000',vnp_Command:'pay',vnp_CreateDate:'20210801153333',vnp_CurrCode:'VND',vnp_IpAddr:'127.0.0.1',vnp_Locale:'vn',vnp_OrderInfo:'Thanh toan don hang :5',vnp_OrderType:'other',vnp_ReturnUrl:'https://domainmerchant.vn/ReturnUrl'};
 const expected='vnp_Amount=1806000&vnp_Command=pay&vnp_CreateDate=20210801153333&vnp_CurrCode=VND&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+don+hang+%3A5&vnp_OrderType=other&vnp_ReturnUrl=https%3A%2F%2Fdomainmerchant.vn%2FReturnUrl&vnp_TmnCode=DEMOV210&vnp_TxnRef=5&vnp_Version=2.1.0';
 assert.equal(canonical(fields),expected);
 assert.equal(hmac(fixture.secret,expected),'ab8c717ebb881816460e4fa235ee58475ad33abe9491bc95b9376e32c80ccecd95a33f298ec14fac6f87e9c428cf7af644a8de55cb1f402034eb7a924f92a0df');
});
test('Unicode, spaces, plus, percent, optional bank code and safe signature comparison roundtrip',()=>{
 const p=new VnpayProvider(fixture),fields={vnp_OrderInfo:'Áo đẹp + 100% & váy',vnp_BankCode:'NCB',vnp_TxnRef:'ABC'};
 const raw=canonical(fields);assert.ok(raw.includes('+'));assert.ok(raw.includes('%2B'));assert.ok(raw.includes('%C3'));
 const signed=parseQuery('/ipn?'+raw+'&vnp_SecureHash='+hmac(fixture.secret,raw)+'&vnp_SecureHashType=HmacSHA512');assert.equal(p.verify(signed),true);
 signed.vnp_TxnRef='XYZ';assert.equal(p.verify(signed),false);signed.vnp_SecureHash='bad';assert.equal(p.verify(signed),false);
});
test('reject duplicate/nested/malformed query values before verification',()=>{
 for(const query of ['vnp_TxnRef=A&vnp_TxnRef=B','vnp_TxnRef=A&vnp_%54xnRef=B','vnp_X[]=1','vnp_X=%ZZ','vnp_X=%FF','other=1'])assert.throws(()=>parseQuery('/ipn?'+query));
 assert.throws(()=>canonical({vnp_Amount:['1']}));assert.throws(()=>canonical({vnp_Amount:1}));
});
test('sandbox config fail closed, VND scaled once with bounds, GMT+7 across day/year',()=>{
 assert.throws(()=>settings({...env,VNPAY_ENV:'production'}));assert.throws(()=>settings({...env,VNPAY_PAYMENT_URL:'https://evil.test/'}));assert.throws(()=>new VnpayProvider({...fixture,secret:''}));
 assert.equal(amountUnits(25000),'2500000');for(const amount of [0,4999,1.1,Infinity,Number.MAX_SAFE_INTEGER,10000000000])assert.throws(()=>amountUnits(amount));
 assert.equal(dateVn(new Date('2026-12-31T18:00:00Z')),'20270101010000');
});
test('PAY uses immutable attempt dates, exact sandbox URL and never accepts client IP fields',()=>{
 const p=new VnpayProvider(fixture),url=new URL(p.paymentUrl({reference:'REF',amount:25000,ip:'203.0.113.7',createDate:'20261027120000',expireDate:'20261027121500'}));
 assert.equal(url.origin,'https://sandbox.vnpayment.vn');assert.equal(url.searchParams.get('vnp_Amount'),'2500000');assert.equal(url.searchParams.get('vnp_ExpireDate'),'20261027121500');
 assert.equal(clientIp({headers:{'x-forwarded-for':'1.2.3.4, 203.0.113.7'},body:{ip:'9.9.9.9'}},{K_SERVICE:'test'}),'203.0.113.7');
 assert.throws(()=>clientIp({headers:{'x-forwarded-for':'1.2.3.4'}},{}));
 assert.equal(clientIp({socket:{remoteAddress:'::ffff:127.0.0.1'},headers:{'x-forwarded-for':'9.9.9.9'}},{FUNCTIONS_EMULATOR:'true'}),'127.0.0.1');
});
function queryResponse(overrides={}){return {vnp_ResponseId:'RESPONSE',vnp_Command:'querydr',vnp_ResponseCode:'00',vnp_Message:'Success',vnp_TmnCode:'TESTONLY',vnp_TxnRef:'REF',vnp_Amount:'2500000',vnp_BankCode:'NCB',vnp_PayDate:'20261027120100',vnp_TransactionNo:'12345',vnp_TransactionType:'01',vnp_TransactionStatus:'00',vnp_OrderInfo:'Query REF',...overrides};}
test('QueryDR official pipe field order incl promotions; verify signed response and correlate reference',async()=>{
 const p=new VnpayProvider({...fixture,fetchImpl:async(url,options)=>{
   assert.equal(url,'https://sandbox.vnpayment.vn/merchant_webapi/api/transaction');const body=JSON.parse(options.body);
   const independent=[body.vnp_RequestId,'2.1.0','querydr','TESTONLY','REF','20261027120000',body.vnp_CreateDate,'203.0.113.5','Query REF'].join('|');
   assert.equal(body.vnp_SecureHash,crypto.createHmac('sha512',fixture.secret).update(independent).digest('hex'));
   const data=queryResponse({vnp_PromotionCode:'PROMO',vnp_PromotionAmount:'100'});data.vnp_SecureHash=hmac(fixture.secret,pipeData(data,QUERY_RESPONSE_FIELDS));return {ok:true,json:async()=>data};
 }});
 const result=await p.query({reference:'REF',createDate:'20261027120000'});assert.equal(result.success,true);assert.equal(result.source,'QUERYDR');
 p.fetch=async()=>({ok:true,json:async()=>({...queryResponse(),vnp_SecureHash:'0'.repeat(128)})});await assert.rejects(p.query({reference:'REF'}),e=>e.code==='data-loss');
});
test('QueryDR timeout/network/not-found remains unknown, never fabricates failure',async()=>{
 const p=new VnpayProvider({...fixture,fetchImpl:async()=>{const e=new Error('sensitive transport');e.name='TimeoutError';throw e;}});
 await assert.rejects(p.query({reference:'REF',createDate:'20261027120000'}),e=>e.code==='deadline-exceeded'&&!e.message.includes('sensitive'));
 p.fetch=async()=>{const d=queryResponse({vnp_ResponseCode:'91'});d.vnp_SecureHash=hmac(fixture.secret,pipeData(d,QUERY_RESPONSE_FIELDS));return {ok:true,json:async()=>d};};
 assert.deepEqual(await p.query({reference:'REF',createDate:'20261027120000'}),{pending:true});
 assert.throws(()=>pipeData({vnp_X:'a|b'},['vnp_X']));
});
test('signed callback cannot change VND currency',()=>{
 const p=new VnpayProvider(fixture);assert.throws(()=>p.normalize({vnp_CurrCode:'USD'}),/currency/);
});

test('Cloud Run sandbox discovers server IP when QueryDR IP is unset and verifies the response',async()=>{
 const calls=[];
 const p=new VnpayProvider({...fixture,env:{...env,K_SERVICE:'sandbox-reconcile',VNPAY_QUERY_IP:''},fetchImpl:async(url,options)=>{
   calls.push(url);
   if(url==='https://api.ipify.org')return {ok:true,text:async()=> '203.0.113.19\n'};
   const body=JSON.parse(options.body);assert.equal(body.vnp_IpAddr,'203.0.113.19');
   const data=queryResponse();data.vnp_SecureHash=hmac(fixture.secret,pipeData(data,QUERY_RESPONSE_FIELDS));
   return {ok:true,json:async()=>data};
 }});
 assert.equal((await p.query({reference:'REF',createDate:'20261027120000'})).success,true);
 assert.deepEqual(calls,['https://api.ipify.org','https://sandbox.vnpayment.vn/merchant_webapi/api/transaction']);
});

test('IP discovery failure or invalid response never sends an invented QueryDR IP',async()=>{
 for(const result of [null,'not-an-ip']){
   let calls=0;
   const p=new VnpayProvider({...fixture,env:{...env,K_SERVICE:'sandbox-reconcile',VNPAY_QUERY_IP:''},fetchImpl:async()=>{
     calls++;if(result===null)throw Error('lookup unavailable');return {ok:true,text:async()=>result};
   }});
   await assert.rejects(p.query({reference:'REF'}));assert.equal(calls,1);
 }
});
