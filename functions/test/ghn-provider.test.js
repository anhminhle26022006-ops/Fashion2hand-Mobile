const {test}=require('node:test');
const assert=require('node:assert/strict');
const {GhnProvider,config,STATUS,transition,parcel}=require('../ghn-provider');
const env={GHN_ENV:'staging',GHN_TOKEN_ENV:'staging'};
const response=data=>({ok:true,status:200,json:async()=>({code:200,data})});
test('shop listing needs no ShopId; auth rejection returns only safe diagnostics',async()=>{
  const logs=[];
  const p=new GhnProvider({env,token:'private-fixture',log:e=>logs.push(e),fetchImpl:async(url,options)=>{
    assert.equal(url.pathname,'/shiip/public-api/v2/shop/all');
    assert.equal(options.method,'GET'); assert.equal(options.headers.ShopId,undefined);
    return {ok:false,status:401,json:async()=>({code:401,message:'private-fixture Authorization password',details:{Token:'private-fixture'}})};
  }});
  await assert.rejects(p.shops(),e=>{
    assert.equal(e.code,'failed-precondition');
    assert.deepEqual(e.details,{reason:'GHN_AUTH_REJECTED',environment:'staging',providerHttpStatus:401,operation:'SHOPS_LIST'});
    assert.ok(!JSON.stringify(e).includes('private-fixture'));return true;
  });
  assert.ok(!JSON.stringify(logs).includes('private-fixture'));
});
test('explicit environment and matching token environment required; missing token never calls network',async()=>{
  assert.throws(()=>config({}),/GHN_ENV/);assert.throws(()=>config({...env,GHN_TOKEN_ENV:'production'}),/khớp/);
  let calls=0;const p=new GhnProvider({env,token:'',fetchImpl:async()=>{calls++;}});
  await assert.rejects(p.shops(),/GHN_TOKEN/);assert.equal(calls,0);
});
test('fixed staging origin, correct headers, grams/cm and fee payload; no server token in error or log',async()=>{
  const logs=[];const p=new GhnProvider({env,token:'test-only-sensitive-value',log:e=>logs.push(e),fetchImpl:async(url,options)=>{
    assert.equal(url.origin,'https://dev-online-gateway.ghn.vn');assert.equal(options.headers.ShopId,'123');assert.equal(options.headers.Token,'test-only-sensitive-value');
    assert.equal(JSON.parse(options.body).weight,600);return response({total:25000});
  }});assert.deepEqual(await p.fee(123,{weight:600}),{total:25000});assert.deepEqual(logs,[]);
  p.fetch=async()=>({ok:false,status:400,json:async()=>({code:400,message:'test-only-sensitive-value phone address'})});
  await assert.rejects(p.fee(123,{}),e=>!e.message.includes('test-only-sensitive-value')&&!e.uncertain);
  assert.ok(!JSON.stringify(logs).includes('test-only-sensitive-value'));
});
test('timeouts and unreadable create responses are uncertain; no automatic create retry',async()=>{
  let calls=0;const p=new GhnProvider({env,token:'test-only',fetchImpl:async()=>{calls++;throw new Error('token or private transport details');}});
  await assert.rejects(p.create(1,{}),e=>e.uncertain&&e.code==='unavailable');assert.equal(calls,1);
  p.fetch=async()=>({ok:false,status:503,json:async()=>({code:503})});await assert.rejects(p.create(1,{}),e=>e.uncertain);
});
test('production mutation blocked unless explicitly enabled; readonly requests permitted',async()=>{
  let calls=0;const p=new GhnProvider({env:{GHN_ENV:'production',GHN_TOKEN_ENV:'production'},token:'test-only',fetchImpl:async()=>{calls++;return response({shops:[]});}});
  await assert.rejects(p.create(1,{}),/production/);await assert.rejects(p.cancel(1,'TEST'),/production/);assert.equal(calls,0);await p.shops();assert.equal(calls,1);
});
test('parcel has no invented defaults, one parcel aggregates weight and stack height, limits explicit',()=>{
  assert.throws(()=>parcel([{}]),/cấu hình/);assert.throws(()=>parcel([{}],{weight:300,length:20,width:15,height:2}),/cấu hình/);
  const estimate={weight:300,length:20,width:15,height:2,estimated:true};
  assert.deepEqual(parcel([{},{}],estimate),{weight:600,length:20,width:15,height:4,estimated:true});
  assert.throws(()=>parcel(new Array(20).fill({}),{...estimate,weight:1000}),/20kg/);
});
test('temporary delivery failure is distinct from return; all documented states mapped',()=>{
  assert.equal(Object.keys(STATUS).length,23);assert.equal(STATUS.delivery_fail,'DELIVERY_FAILED');assert.equal(STATUS.return_fail,'RETURN_FAILED');assert.equal(STATUS.returned,'RETURNED');
  assert.notEqual(STATUS.waiting_to_return,'RETURNED');assert.equal(STATUS.delivered,'DELIVERED');
});
test('stale, concurrent-equal timestamp and duplicate events do not rewind terminal state',()=>{
  const prior={providerStatus:'delivered',normalizedStatus:'DELIVERED',providerUpdatedAt:'2026-10-26T10:00:00Z'};
  assert.equal(transition(prior,{status:'delivered',updated_date:prior.providerUpdatedAt}),false);
  assert.equal(transition(prior,{status:'delivering',updated_date:'2026-10-26T09:00:00Z'}),false);
  assert.equal(transition(prior,{status:'delivery_fail',updated_date:'2026-10-26T11:00:00Z'}),false);
  assert.throws(()=>transition({}, {status:'invented',updated_date:prior.providerUpdatedAt}),/Trạng thái/);
});

test('GHN address catalogue validates hierarchy and environment; Firestore IDs are not GHN codes',async()=>{
 const p=new GhnProvider({env,token:'test-only',fetchImpl:async url=>response(url.pathname.endsWith('/province')?[{ProvinceID:1,ProvinceName:'Province'}]:url.pathname.endsWith('/district')?[{DistrictID:2,DistrictName:'District'}]:[{WardCode:'3',WardName:'Ward'}])});
 const source={recipient:'Buyer',phone:'0901234567',street:'Street'};
 await assert.rejects(p.address(10,source),/chưa có mã GHN/);
 await assert.rejects(p.address(10,{...source,ghn:{environment:'production',provinceId:1,districtId:2,wardCode:'3'}}),/môi trường khác/);
 await assert.rejects(p.address(10,{...source,ghn:{environment:'staging',provinceId:1,districtId:99,wardCode:'3'}}),/không thuộc/);
 const address=await p.address(10,{...source,ghn:{environment:'staging',provinceId:1,districtId:2,wardCode:'3'}});
 assert.equal(address.ghn.environment,'staging');assert.equal(address.ward,'Ward');assert.equal(address.ghn.addressModel,'GHN_LEGACY');
});

test('quote fingerprints ignore nested object key order but preserve item array order',()=>{
 const {hash}=require('../ghn-provider');
 assert.equal(hash({sellers:{z:{shop:2},a:{shop:1}},address:{ward:3,province:1}}),hash({address:{province:1,ward:3},sellers:{a:{shop:1},z:{shop:2}}}));
 assert.notEqual(hash({items:['first','second']}),hash({items:['second','first']}));
});
