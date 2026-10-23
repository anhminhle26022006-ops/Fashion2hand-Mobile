# Kết quả deploy Firebase cloud — 24/10/2026

Project: **fashion2hand** (1096676251064). Source: nhánh **MINH-ANH** trong workspace, gồm các thay đổi chưa commit. Người dùng đã yêu cầu deploy sau khi đăng nhập lại Firebase.

## Kết quả

- Phiên Firebase CLI hợp lệ; project ACTIVE.
- Firestore `(default)`: **STANDARD**, Firestore Native, location `nam5`.
- Dry run thành công; Firestore Rules và Storage Rules biên dịch thành công.
- Lệnh deploy thật kết thúc **Deploy complete**, exit code 0.
- **39/39 Cloud Functions ACTIVE**, runtime **nodejs22**, region **us-central1**. Bao gồm 22 hàm mới và 17 hàm cập nhật từ bản cloud trước đó.
- Firestore Rules đã release lên `cloud.firestore`.
- Storage Rules đã release lên `firebase.storage`.
- Firestore indexes đã triển khai và đọc lại được trên cloud, gồm `orders.orderStatus + inspectionDeadline`.
- Không dùng `--force`, không xóa functions/collection/document, không bật billing hay đổi gói tài khoản.
- Không thay đổi giá trị secret. Hai webhook tiếp tục dùng secret cloud hiện có (`PAYMENT_WEBHOOK_TOKEN`, `SHIPMENT_WEBHOOK_TOKEN`).

Lệnh đã chạy:

```powershell
firebase deploy --project fashion2hand --only 'functions,firestore:rules,firestore:indexes,storage' --dry-run --non-interactive
firebase deploy --project fashion2hand --only 'functions,firestore:rules,firestore:indexes,storage' --non-interactive
```

## Kiểm tra sau deploy

Thời điểm kiểm tra cuối: **24/10/2026 02:14:45, giờ Việt Nam**.

| Kiểm tra | HTTP | Kết quả |
|---|---|---|
| getTransactionCapabilities không đăng nhập | 401 UNAUTHENTICATED | Đạt |
| createCartOrders không đăng nhập | 401 UNAUTHENTICATED | Đạt |
| getChatContext không đăng nhập | 401 UNAUTHENTICATED | Đạt |
| sendMessage không đăng nhập | 401 UNAUTHENTICATED | Đạt |
| updatePaymentFromProvider không có secret | 401 | Đạt |
| updateShipmentFromCarrier không có secret | 401 | Đạt |
| Đọc document orders không đăng nhập | 403 | Đạt |

Kiểm tra chỉ gửi yêu cầu không đủ quyền và đọc metadata triển khai; không tạo tài khoản, đơn hàng, thanh toán hoặc dữ liệu thử nghiệm trên cloud. Một probe đầu tiên dùng ID dạng `__...__` bị Firestore từ chối 400 do ID dành riêng; đã sửa ID hợp lệ và chạy lại nguyên kiểm tra quyền, đạt 403.

Bằng chứng máy cục bộ (không chứa giá trị secret): `.gradle/deploy-cloud/function-status.json`, `.gradle/deploy-cloud/smoke-results.json`.

Đây là xác minh triển khai và kiểm soát truy cập, **chưa phải E2E Android bằng hai tài khoản thực trên cloud**. Các test nghiệp vụ trước đó chạy trên Emulator được ghi riêng trong `MINH_ANH_IMPLEMENTATION.md`.

## Sử dụng app

APK debug hiện tại đã build với `USE_FIREBASE_EMULATORS=false`. Có thể chạy từ Android Studio với cấu hình mặc định, hoặc build bằng:

```powershell
.\build-debug.ps1 -RunUnitTests
```

Không truyền `-PuseFirebaseEmulators=true` khi muốn dùng cloud.

Thanh toán và vận chuyển mô phỏng vẫn bị backend khóa ngoài Emulator theo thiết kế hiện tại. Việc deploy không đồng nghĩa đã kết nối VNPAY/GHN/GHTK hoặc chuyển tiền thật. Chat, yêu cầu nhận đồ, giỏ, tạo đơn và dữ liệu backend đã có endpoint cloud; quá trình thanh toán/giao hàng đầy đủ cần adapter/cấu hình được mô tả trong báo cáo triển khai.

Webhook vận chuyển yêu cầu `eventId` duy nhất. Chỉ backend đối tác có secret hợp lệ được gửi cập nhật.

[Firebase Console](https://console.firebase.google.com/project/fashion2hand/overview)