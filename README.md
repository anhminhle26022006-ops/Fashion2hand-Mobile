# Fashion2hand

Ứng dụng Android mua bán và cho tặng quần áo đã qua sử dụng, phục vụ đồ án môn học. Ứng dụng viết bằng Java, giao diện XML; backend dùng Firebase Authentication, Firestore, Storage và Cloud Functions JavaScript. Có tích hợp VNPAY Sandbox và GHN staging.

## Build và chạy

Mở repository bằng Android Studio, đồng bộ Gradle và chọn thiết bị Android API 29 trở lên. Cấu hình Firebase Android: `app/google-services.json`.

```powershell
.\build-debug.ps1
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Debug mặc định và release dùng Firebase thật. Build APK không triển khai backend.

Để thử bằng Firebase Emulator, cài dependency bằng `npm ci` trong `functions/`, chuẩn bị Firebase CLI rồi chạy hai terminal tại thư mục gốc:

```powershell
# Terminal 1
.\start-emulators.ps1
```

```powershell
# Terminal 2
.\build-debug.ps1 -UseFirebaseEmulators
```

Android Emulator dùng host `10.0.2.2`; dữ liệu Emulator tách riêng cloud. Chạy unit test bằng `.\build-debug.ps1 -RunUnitTests`. Chạy test thiết bị bằng `.\build-debug.ps1 -RunDeviceTests` khi Emulator và thiết bị đã sẵn sàng.

## Tài liệu

- [Thiết lập Firebase và Android](docs/README_VI.md).
- [Mô hình Firestore](docs/FIRESTORE_DATA_MODEL_VI.md), [danh mục địa chỉ](docs/ADDRESS_DATA.md), [chính sách xác minh](docs/CONTACT_VERIFICATION_POLICY.md).
- [Tích hợp VNPAY](docs/VNPAY_INTEGRATION.md), [luồng thanh toán](docs/VNPAY_PAYMENT_FLOW.md), [đối soát](docs/VNPAY_RECONCILIATION_NOTES.md), [hỗ trợ Sandbox](docs/VNPAY_SANDBOX_SUPPORT.md).
- [Tích hợp GHN](docs/GHN_INTEGRATION.md), [địa chỉ và checkout](docs/CHECKOUT_GHN_GUIDE.md), [điểm lấy hàng](docs/GHN_SELLER_PICKUP.md), [chẩn đoán GHN](docs/GHN_CLOUD_DIAGNOSTICS.md), [sự cố đồng bộ shop](docs/GHN_PICKUP_INCIDENT.md).
- [Bản đồ vận chuyển](docs/TRACKING_MAP.md), [quản trị và fixture kiểm thử](docs/ADMIN_CHECKLIST.md), [đổi mật khẩu admin](docs/ADMIN_PASSWORD_RESET.md).
- [Rà soát giao diện/backend](docs/UI_BACKEND_AUDIT.md), [kết quả kiểm thử](docs/QA_BACKEND_UI_RESULTS.md), [kiểm thử responsive](docs/QA_RESPONSIVE_CHECKLIST.md). Kết quả áp dụng theo thời điểm và phạm vi trong từng tài liệu.
- [Website giới thiệu](docs/HOSTING.md).

Hướng dẫn công cụ AI nằm trong `.agents/skills/`. Khóa bí mật của nhà cung cấp được cấu hình phía backend, không đưa vào ứng dụng Android.
