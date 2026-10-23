const LOCAL_HOSTS = { FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099", FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080" };
for (const [key, host] of Object.entries(LOCAL_HOSTS)) {
  if (!process.env[key]) process.env[key] = host;
  if (process.env[key] !== host) throw new Error(`${key} phải là ${host}. Script chỉ chạy trên Emulator.`);
}

const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const PROJECT = "fashion2hand";
const FUNCTIONS_URL = `http://127.0.0.1:5001/${PROJECT}/us-central1`;
const PASSWORD = "Test-only-123!";
initializeApp({ projectId: PROJECT });
const auth = getAuth();
const db = getFirestore();
const now = () => FieldValue.serverTimestamp();

const USERS = {
  admin: { email: "admin@fashion2hand.test", name: "Quản trị viên", role: "ADMIN", verificationStatus: "VERIFIED" },
  thu: { email: "thu@fashion2hand.test", name: "Thư (người bán)", role: "USER", verificationStatus: "VERIFIED" },
  minhanh: { email: "minhanh@fashion2hand.test", name: "Minh Anh (người bán)", role: "USER", verificationStatus: "VERIFIED" },
  nhi: { email: "nhi@fashion2hand.test", name: "Nhi (người mua)", role: "USER", verificationStatus: "UNVERIFIED" },
  spam: { email: "spam@fashion2hand.test", name: "Tài khoản đáng ngờ", role: "USER", verificationStatus: "UNVERIFIED" }
};

const CATEGORIES = ["Áo", "Quần", "Váy", "Giày", "Túi", "Phụ kiện"];

const POSTS = [
  { key: "aoKhoac", owner: "thu", title: "Áo khoác jean vintage", type: "SALE", price: 120000, category: "Áo", size: "M", condition: "GOOD" },
  { key: "giay", owner: "minhanh", title: "Giày sneaker trắng", type: "SALE", price: 250000, category: "Giày", size: "40", condition: "FAIR" },
  { key: "quanTay", owner: "minhanh", title: "Quần tây đen công sở", type: "SALE", price: 90000, category: "Quần", size: "L", condition: "GOOD" },
  { key: "somi", owner: "thu", title: "Áo sơ mi trắng", type: "SALE", price: 80000, category: "Áo", size: "S", condition: "LIKE_NEW" },
  { key: "vayHoa", owner: "thu", title: "Váy hoa nhí", type: "FREE", price: 0, category: "Váy", size: "S", condition: "LIKE_NEW" },
  { key: "aoThun", owner: "minhanh", title: "Áo thun basic", type: "FREE", price: 0, category: "Áo", size: "M", condition: "GOOD" },
  { key: "khanChoang", owner: "thu", title: "Khăn choàng len", type: "FREE", price: 0, category: "Phụ kiện", size: "Free size", condition: "GOOD" },
  { key: "tuiGia", owner: "spam", title: "Túi hàng hiệu giá rẻ bất ngờ", type: "SALE", price: 50000, category: "Túi", size: "Free size", condition: "NEW" }
];

async function createUser(key, info) {
  let record;
  try {
    record = await auth.getUserByEmail(info.email);
    await auth.updateUser(record.uid, { password: PASSWORD, displayName: info.name, disabled: false });
  } catch (error) {
    if (error.code !== "auth/user-not-found") throw error;
    record = await auth.createUser({ email: info.email, password: PASSWORD, displayName: info.name, emailVerified: true });
  }
  await auth.setCustomUserClaims(record.uid, info.role === "ADMIN" ? { admin: true } : {});
  await db.doc(`users/${record.uid}`).set({
    uid: record.uid, name: info.name, email: info.email, role: info.role,
    accountStatus: "ACTIVE", verificationStatus: info.verificationStatus,
    createdAt: now(), updatedAt: now()
  });
  await db.doc(`users/${record.uid}/addresses/home`).set({
    recipient: info.name, phone: "0901234567", province: "TP. Hồ Chí Minh",
    district: "Quận 1", ward: "Bến Nghé", street: "59C Nguyễn Đình Chiểu"
  });
  return { key, uid: record.uid, email: info.email, token: await signIn(info.email) };
}

async function signIn(email) {
  const response = await fetch(`http://${LOCAL_HOSTS.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=emulator`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password: PASSWORD, returnSecureToken: true })
  });
  const body = await response.json();
  if (!response.ok) throw new Error(`Không đăng nhập được ${email}: ${body.error?.message}`);
  return body.idToken;
}

async function call(name, data, user) {
  let response;
  try {
    response = await fetch(`${FUNCTIONS_URL}/${name}`, {
      method: "POST",
      headers: { "content-type": "application/json", authorization: `Bearer ${user.token}` },
      body: JSON.stringify({ data }),
      signal: AbortSignal.timeout(60000)
    });
  } catch {
    throw new Error("Không gọi được Functions Emulator (cổng 5001). Hãy chạy start-emulators.ps1 trước.");
  }
  const body = await response.json();
  if (!response.ok) throw new Error(`${name} lỗi: ${body.error?.message || response.status}`);
  return body.result;
}

async function createPost(post, owner) {
  const ref = db.collection("posts").doc();
  await ref.set({
    ownerId: owner.uid, title: post.title, description: `${post.title} - dữ liệu mẫu Emulator.`,
    type: post.type, price: post.price, category: post.category, size: post.size,
    condition: post.condition, location: "TP. Hồ Chí Minh", imageUrls: [],
    status: "ACTIVE", createdAt: now(), updatedAt: now()
  });
  return ref.id;
}

async function paidOrderUntilShipping(postId, buyer, seller) {
  const { orderId } = await call("createPaidOrder", { postId, addressId: "home", idempotencyKey: `seed-${postId}` }, buyer);
  await call("simulatePaymentResult", { orderId, status: "PAID" }, buyer);
  await call("updateOrderStatus", { orderId, status: "PREPARING" }, seller);
  await call("updateOrderStatus", { orderId, status: "READY_TO_SHIP" }, seller);
  await call("createShipment", { orderId, carrier: "MOCK_CARRIER" }, seller);
  return orderId;
}

async function seed() {
  const marker = db.doc("seedInfo/emulator");
  if ((await marker.get()).exists) {
    console.log("Emulator đã có dữ liệu mẫu. Muốn nạp lại thì tắt Emulator (dữ liệu trắng) rồi chạy lại.");
    return null;
  }

  const u = {};
  for (const [key, info] of Object.entries(USERS)) u[key] = await createUser(key, info);

  const batch = db.batch();
  CATEGORIES.forEach((name, index) => batch.set(db.collection("categories").doc(), { name, enabled: true, sortOrder: index + 1, updatedAt: now() }));
  batch.set(db.doc("systemConfig/active"), {
    shippingFee: 15000, maxPrice: 2000000, requirePhoneBeforeSelling: false,
    autoHideReported: false, reportThreshold: 5, autoCompleteDays: 3,
    revision: 1, updatedAt: now(), updatedBy: u.admin.uid
  });
  batch.set(db.doc("shippingTariffs/active"), { carrier: "MOCK_CARRIER", currency: "VND", minFee: 15000, status: "ACTIVE", updatedAt: now() }, { merge: true });
  await batch.commit();

  const p = {};
  for (const post of POSTS) p[post.key] = await createPost(post, u[post.owner]);

  const completed = await paidOrderUntilShipping(p.quanTay, u.nhi, u.minhanh);
  await call("simulateShipmentUpdate", { orderId: completed, status: "DELIVERED", description: "Đã giao thành công" }, u.minhanh);
  await call("updateOrderStatus", { orderId: completed, status: "COMPLETED" }, u.nhi);

  const disputed = await paidOrderUntilShipping(p.giay, u.thu, u.minhanh);
  await call("simulateShipmentUpdate", { orderId: disputed, status: "DELIVERED", description: "Đã giao thành công" }, u.minhanh);
  await call("createDispute", {
    orderId: disputed, reason: "DAMAGED",
    description: "Giày bị bong đế, trong bài đăng ghi tình trạng còn tốt.",
    uploadKey: `seed-${disputed}`, evidencePaths: []
  }, u.thu);

  const shipping = await paidOrderUntilShipping(p.somi, u.nhi, u.thu);

  const freeRequest = await call("createFreeRequest", { postId: p.aoThun, addressId: "home", note: "Mình xin áo để đi làm thêm ạ." }, u.nhi);
  const { orderId: freeOrder } = await call("respondFreeRequest", { requestId: freeRequest.requestId, accept: true }, u.minhanh);
  await call("updateOrderStatus", { orderId: freeOrder, status: "PREPARING" }, u.minhanh);

  await call("createFreeRequest", { postId: p.vayHoa, addressId: "home", note: "Cho mình xin váy với nhé." }, u.nhi);

  const reports = [
    { reporterId: u.nhi.uid, targetType: "POST", targetId: p.tuiGia, reason: "Hàng giả / lừa đảo", description: "Giá quá rẻ so với hàng hiệu, nghi hàng giả." },
    { reporterId: u.thu.uid, targetType: "USER", targetId: u.spam.uid, reason: "Spam tin nhắn", description: "Liên tục nhắn tin quảng cáo." },
    { reporterId: u.minhanh.uid, targetType: "POST", targetId: p.tuiGia, reason: "Nội dung sai sự thật", description: "Ảnh lấy từ trang bán hàng khác." }
  ];
  for (const report of reports) await db.collection("reports").add({ ...report, status: "PENDING", createdAt: now(), updatedAt: now() });

  await db.collection("verificationRequests").add({ userId: u.nhi.uid, requestType: "IDENTITY", status: "PENDING", evidencePaths: [], createdAt: now(), updatedAt: now() });

  await marker.set({ seededAt: now(), users: Object.values(USERS).map((info) => info.email) });
  return { u, orders: { completed, disputed, shipping, freeOrder } };
}

seed()
  .then((result) => {
    if (!result) process.exit(0);
    console.log("Đã nạp dữ liệu mẫu vào Emulator.");
    console.log(`Mật khẩu chung: ${PASSWORD}`);
    for (const user of Object.values(result.u)) console.log(`  ${USERS[user.key].role.padEnd(5)} ${user.email}`);
    console.log("Đơn mẫu: 1 hoàn tất, 1 đang khiếu nại, 1 đang giao, 1 đơn cho tặng đang chuẩn bị, 1 yêu cầu nhận đồ chờ duyệt.");
    process.exit(0);
  })
  .catch((error) => {
    console.error("Nạp dữ liệu thất bại:", error.message);
    process.exit(1);
  });