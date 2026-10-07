# SecureVault (Project_GK) - Mật mã Kho Dữ liệu Cá nhân 🔐

**SecureVault** là ứng dụng Android chuyên dụng để lưu trữ và quản lý các thông tin nhạy cảm (Mật khẩu, Thẻ ngân hàng, Ghi chú bảo mật) với độ an toàn cao. Ứng dụng tích hợp công nghệ **Mã hóa cơ sở dữ liệu SQLCipher (AES-256)**, **Android KeyStore**, **Xác thực sinh trắc học BiometricPrompt** và cơ chế **Tự động khóa theo vòng đời ứng dụng**.

---

## 🌟 Tính năng chính

1. **Mã hóa Cơ sở dữ liệu Room + SQLCipher (AES-256)**
   - Toàn bộ dữ liệu ghi chú bảo mật được mã hóa cấp độ quân sự AES-256 trước khi lưu xuống đĩa.
   - Sử dụng Room ORM kết hợp với `net.zetetic:android-database-sqlcipher`.

2. **Quản lý Khóa an toàn với Hardware-backed Android KeyStore**
   - Khóa Master Key (`vault_master_key`) được sinh và lưu trữ an toàn trong vùng cứng bảo mật của thiết bị thông qua `KeyStoreManager`.
   - Passphrase giải mã database được mã hóa và lưu trữ bằng `EncryptedSharedPreferences`.

3. **Xác thực Sinh trắc học & Master PIN**
   - Hỗ trợ xác thực vân tay / khuôn mặt thông qua `BiometricPrompt`.
   - Hỗ trợ xác thực dự phòng bằng **Master PIN 6 số** với giao diện tùy chọn ẩn/hiện mã PIN trực quan.

4. **Chống chụp màn hình & Tự động khóa kho (Auto-Lock)**
   - Bật cờ `FLAG_SECURE` chặn toàn bộ hành vi chụp màn hình, quay phim hoặc xem trước trong ứng dụng gần đây.
   - Sử dụng `ProcessLifecycleOwner` và `AppLifecycleTracker` để tự động khóa kho dữ liệu ngay lập tức khi ứng dụng bị chuyển sang background.

5. **Phân loại, Sắp xếp & Xem chi tiết dữ liệu**
   - Phân loại rõ ràng: 🏛️ Thẻ ngân hàng, 🔑 Mật khẩu, 📝 Ghi chú.
   - Sắp xếp tự động ghi chú mới nhất lên đầu (`ORDER BY timestamp DESC`).
   - Nhấp vào từng ghi chú để giải mã xem chi tiết và **Sao chép nội dung vào Clipboard** nhanh chóng.

6. **Công cụ Đo đạc Hiệu năng (Benchmark Utility)**
   - Tích hợp `BenchmarkManager` cho phép đo đạc và so sánh thời gian thực thi ghi dữ liệu hàng loạt (1,000 bản ghi) giữa SQLite thông thường và SQLCipher mã hóa AES-256.

---

## 🏗️ Kiến trúc & Công nghệ Sử dụng

- **Ngôn ngữ**: Java 17
- **Target SDK**: 35 | **Compile SDK**: 36 | **Min SDK**: 26
- **Kiến trúc**: Repository Pattern, DAO Pattern, Lifecycle-Aware Architecture
- **Thư viện chính**:
  - `androidx.room:room-runtime:2.6.1`: Khung quản lý cơ sở dữ liệu ORM.
  - `net.zetetic:android-database-sqlcipher:4.5.4`: Động cơ mã hóa SQLCipher AES-256.
  - `androidx.biometric:biometric:1.1.0`: Thư viện xác thực sinh trắc học Android.
  - `androidx.security:security-crypto:1.1.0-alpha06`: EncryptedSharedPreferences & MasterKeys.
  - `androidx.lifecycle:lifecycle-process:2.7.0`: Theo dõi vòng đời toàn ứng dụng.

---

## 📂 Cấu trúc Mã nguồn Dự án

```text
com.example.project_gk/
├── AppDatabase.java            # Khởi tạo Room Database (SQLCipher AES-256 & SQLite tiêu chuẩn)
├── AppLifecycleTracker.java    # Theo dõi vòng đời App để tự động khóa khi chuyển background
├── BenchmarkManager.java       # Bộ đo đạc hiệu năng ghi batch 1,000 bản ghi
├── KeyStoreManager.java        # Khởi tạo và quản lý AES Key trong Android KeyStore
├── MainActivity.java           # Điều khiển giao diện chính, sinh trắc học và quản lý trạng thái
├── NoteAdapter.java            # RecyclerView Adapter hiển thị danh sách ghi chú
├── NoteDao.java                # Data Access Object định nghĩa truy vấn Room
├── SecureNote.java             # Entity đại diện cho bảng ghi chú mã hóa
└── VaultAuthRepository.java    # Quản lý Passphrase database mã hóa với EncryptedSharedPreferences
```

---

## 🚀 Hướng dẫn Biên dịch & Cài đặt

1. **Yêu cầu hệ thống**:
   - Android Studio Jellyfish / Ladybug hoặc mới hơn.
   - JDK 17.
   - Thiết bị thật hoặc Emulator chạy Android 8.0 (API level 26) trở lên.

2. **Các bước thực hiện**:
   ```bash
   # 1. Clone repository
   git clone <repository_url>

   # 2. Mở dự án trong Android Studio

   # 3. Biên dịch ứng dụng bằng Gradle
   ./gradlew assembleDebug
   ```

3. **Tài khoản PIN mặc định**:
   - Mã Master PIN mặc định: `123456`

---

## 🌐 Mở rộng Đa ngôn ngữ & Giao diện Light/Dark (Optional)

- **Đa ngôn ngữ**: Toàn bộ chuỗi văn bản đã được chuẩn hóa để hỗ trợ cấu hình trong `res/values/strings.xml` (Tiếng Việt) và `res/values-en/strings.xml` (Tiếng Anh).
- **Giao diện Light/Dark**: Hỗ trợ chuyển đổi chủ đề sáng tối tự động theo hệ thống dựa trên cấu hình trong `res/values/themes.xml` và `res/values-night/themes.xml`.
