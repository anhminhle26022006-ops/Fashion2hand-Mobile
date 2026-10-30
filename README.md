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

- [Thiết lập Firebase và Android](README_VI.md).
- [Mô hình Firestore](FIRESTORE_DATA_MODEL_VI.md), [danh mục địa chỉ](ADDRESS_DATA.md), [chính sách xác minh](CONTACT_VERIFICATION_POLICY.md).
- [Tích hợp VNPAY](VNPAY_INTEGRATION.md), [luồng thanh toán](VNPAY_PAYMENT_FLOW.md), [đối soát](VNPAY_RECONCILIATION_NOTES.md), [hỗ trợ Sandbox](VNPAY_SANDBOX_SUPPORT.md).
- [Tích hợp GHN](GHN_INTEGRATION.md), [địa chỉ và checkout](CHECKOUT_GHN_GUIDE.md), [điểm lấy hàng](functions/scripts/GHN_SELLER_PICKUP.md), [chẩn đoán GHN](functions/scripts/GHN_CLOUD_DIAGNOSTICS.md), [sự cố đồng bộ shop](functions/scripts/GHN_PICKUP_INCIDENT.md).
- [Bản đồ vận chuyển](TRACKING_MAP.md), [quản trị và fixture kiểm thử](ADMIN_CHECKLIST.md), [đổi mật khẩu admin](functions/scripts/ADMIN_PASSWORD_RESET.md).
- [Rà soát giao diện/backend](UI_BACKEND_AUDIT.md), [kết quả kiểm thử](QA_BACKEND_UI_RESULTS.md), [kiểm thử responsive](QA_RESPONSIVE_CHECKLIST.md). Kết quả áp dụng theo thời điểm và phạm vi trong từng tài liệu.
- [Website giới thiệu](hosting/README.md).

Hướng dẫn công cụ AI nằm trong `.agents/skills/`. Khóa bí mật của nhà cung cấp được cấu hình phía backend, không đưa vào ứng dụng Android.
