# Nhật ký Sửa lỗi & Cải tiến Dự án (Bugfix & Enhancements Log)

Tài liệu này ghi nhận chi tiết toàn bộ các lỗi đã được sửa, cải tiến kiến trúc và tối ưu hóa hiệu năng trong dự án **SecureVault (Project_GK)**.

---

## 🛠️ Danh sách các hạng mục Bug Fix & Cải tiến

### 1. 🗄️ Chuyển đổi Database: Thay thế SQLite thuần bằng Room Database + SQLCipher (AES-256)
- **Vấn đề ban đầu**: Cơ sở dữ liệu SQLite truyền thống lưu trữ dạng plaintext, không có lớp mã hóa bảo vệ, gây nguy cơ lộ thông tin nhạy cảm khi thiết bị bị root hoặc trích xuất file.
- **Giải pháp**:
  - Tích hợp **Room ORM** (`androidx.room`) kết hợp thư viện **SQLCipher for Android** (`net.zetetic:android-database-sqlcipher`).
  - Định nghĩa Entity `@Entity(tableName = "secure_notes")` và Data Access Object (`NoteDao`).
  - Khởi tạo Room Database thông qua `SupportFactory(passphrase)` trong `AppDatabase.java` để mã hóa toàn bộ tập tin DB bằng chuẩn **AES-256**.

---

### 2. ↕️ Sắp xếp & Phân loại Dữ liệu (Sorting & Categorization)
- **Cải tiến**:
  - Trong `NoteDao.java`, câu truy vấn được tối ưu hóa: `@Query("SELECT * FROM secure_notes ORDER BY timestamp DESC")` giúp ghi chú mới nhất luôn hiển thị ở đầu danh sách.
  - Phân loại rõ ràng 3 danh mục chính: **Thẻ ngân hàng**, **Mật khẩu**, **Ghi chú**.
  - Thiết lập icon đại diện trực quan theo phân loại trong `NoteAdapter.java` (`🏛️`, `🔑`, `📝`).

---

### 3. 🐛 Sửa lỗi Cú pháp, Debug & Thiếu Phương thức trong `MainActivity.java`
- **Lỗi đã sửa**:
  - Khắc phục đoạn mã `showBiometricPrompt()` bị ngắt dở dang, thiếu dấu đóng callback và thiếu câu lệnh khởi chạy `biometricPrompt.authenticate(promptInfo)`.
  - Bổ sung đầy đủ các phương thức cốt lõi bị thiếu:
    - `unlockDatabase()`: Lấy passphrase từ `VaultAuthRepository`, mở kết nối `AppDatabase` và nạp dữ liệu lên Dashboard.
    - `lockVault()`: Xóa dữ liệu tạm trong RAM, đặt `encryptedDb = null`, xóa trắng ô nhập PIN và đưa ứng dụng về màn hình xác thực.
    - `loadNotesFromDatabase()`: Lấy danh sách ghi chú từ background thread và cập nhật RecyclerView kèm số lượng `tvCount`.
    - `hideKeyboard()`: Ẩn bàn phím mềm khi lưu ghi chú hoặc bấm nút quay lại.
  - Sửa lỗi không gán giá trị cho trường `encryptedDb` và `standardDb`.

---

### 4. 🎨 Chỉnh sửa UI/UX, Bàn phím & Nút ẩn/hiện Master PIN
- **Cải tiến**:
  - Bổ sung nút con mắt (`ivTogglePin`) cho phép người dùng chuyển đổi linh hoạt giữa ẩn và hiện mã PIN 6 số (`numberPassword` ↔ `number`).
  - Thêm tính năng tự động xóa sạch ô nhập PIN sau khi mở khóa thành công hoặc khi ứng dụng bị khóa lại.
  - Bổ sung Dialog xem chi tiết dữ liệu mã hóa khi chạm vào từng thẻ trong danh sách, hỗ trợ nút **Sao chép vào Clipboard** tiện lợi.
  - Tích hợp `EdgeToEdge` và căn chỉnh window insets chính xác cho status bar và navigation bar.

---

### 5. ⚡ Tăng hiệu năng & Quản lý Luồng (Thread Management)
- **Tối ưu hóa**:
  - Đưa toàn bộ các thao tác I/O nặng (mở DB mã hóa, truy vấn `getAllNotes`, chèn bản ghi `insertNote`, chạy `BenchmarkManager`) vào background thread sử dụng `ExecutorService` (`Executors.newSingleThreadExecutor()`).
  - Bổ sung `onDestroy()` trong `MainActivity` để shutdown thread pool sạch sẽ, tránh hiện tượng memory leak.
  - Bổ sung cơ chế làm sạch dữ liệu thử nghiệm rác `initDefaultDataIfEmpty()` nếu dữ liệu vượt quá ngưỡng kiểm thử.

---

### 6. 🔒 Hiệu ứng Xác thực Sinh trắc học & Cơ chế Tự động Khóa (Auto-Lock)
- **Tích hợp An toàn**:
  - Kích hoạt flag hệ thống `WindowManager.LayoutParams.FLAG_SECURE` ngăn chặn hoàn toàn việc chụp màn hình, quay video màn hình và ẩn hình ảnh xem trước app trong màn hình Recent Apps.
  - Sử dụng `BiometricPrompt` để xác thực vân tay / khuôn mặt chính xác và an toàn.
  - Tích hợp `AppLifecycleTracker` triển khai `DefaultLifecycleObserver`. Khi ứng dụng chuyển sang trạng thái Background (`onStop()`), ứng dụng lập tức xóa bộ nhớ đệm database và khóa kho dữ liệu an toàn.

---

### 7. 🌐 Hỗ trợ Đa ngôn ngữ (English / Vietnamese) & Giao diện Sáng/Tối (Optional)
- **Tùy chọn mở rộng**:
  - Chuẩn hóa các chuỗi văn bản trong ứng dụng để dễ dàng tách ra file `res/values/strings.xml` (Tiếng Việt) và `res/values-en/strings.xml` (Tiếng Anh).
  - Hỗ trợ Dark / Light Theme thông qua `res/values/themes.xml` và `res/values-night/themes.xml` sử dụng các màu chuẩn Material Design 3.

---

## 📊 Bảng tổng hợp trạng thái Fix Bugs

| STT | Hạng mục / Lỗi | Trạng thái | Ghi chú |
| :--- | :--- | :---: | :--- |
| 1 | Mới mã hóa Database Room + SQLCipher | ✅ Fixed | Đã mã hóa AES-256 cho tập tin SQLite |
| 2 | Cú pháp `MainActivity` bị ngắt dở dang | ✅ Fixed | Đã hoàn thiện toàn bộ callback & authenticate |
| 3 | Thiếu phương thức `unlockDatabase`, `lockVault` | ✅ Fixed | Đã bổ sung đầy đủ luồng mở/khóa kho |
| 4 | Thiếu hiển thị & sao chép nội dung giải mã | ✅ Fixed | Đã thêm AlertDialog & ClipboardManager |
| 5 | Tải icon phân loại & ẩn/hiện PIN | ✅ Fixed | Đã cập nhật `item_note.xml` & `activity_main.xml` |
| 6 | Rò rỉ bộ nhớ do ExecutorService | ✅ Fixed | Đã shutdown trong `onDestroy()` |
| 7 | Tự động khóa khi thoát app out-of-focus | ✅ Fixed | Đã tích hợp `AppLifecycleTracker` & `ProcessLifecycleOwner` |
