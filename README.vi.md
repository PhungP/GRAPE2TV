# GRAPE2TV

Launcher cho Android TV và Google TV với màn hình chính gọn gàng, thời tiết địa phương, điều khiển nhanh nhạc đang
phát ngầm và nhiều kiểu giao diện: mặc định, **Liquid Glass** (phong cách Apple) và **Sony Xperia**.

Ngôn ngữ: [English](README.md) · Tiếng Việt

GRAPE2TV là bản fork của [CleanYourTV](https://github.com/Gathin23/cleanyourtv) do Gathin phát triển (giấy phép
MIT). Các script gỡ bloatware, theme và ghi chú thiết bị của dự án gốc không nằm trong repo này.

## Bản fork này thêm gì

- **Thời tiết địa phương** trên thanh trên cùng: biểu tượng, nhiệt độ, tình trạng, thành phố và nhiệt độ thấp/cao
  trong ngày. Dữ liệu từ [Open-Meteo](https://open-meteo.com) (không cần tài khoản hay khoá).
- **Đang phát**: pill trên thanh trên cùng hiện tên bài và nghệ sĩ của app đang phát nhạc ngầm. Bấm OK mở bảng có
  ảnh bìa, thanh tiến trình, Trước, Phát/Tạm dừng, Sau và "Mở ứng dụng".
- **Kiểu giao diện**, chọn trong Cài đặt launcher (nút Settings trên thanh trên cùng): Mặc định, Liquid Glass (ô
  trong mờ, viền bẻ sáng, bo góc lớn) và Sony Xperia (phẳng, nền đen, ô vuông bo góc).
- **Thẻ đang phát** theo kiểu máy nghe nhạc bằng kính: ảnh bìa, tên bài và nghệ sĩ, nút sóng âm để mở app, thanh
  tiến trình có núm (Trái/Phải tua 10 giây) và Trước / Phát-Tạm dừng / Sau. Pill trên thanh trên cùng cho chữ chạy
  khi tên bài dài.
- **Lịch tháng** dưới đồng hồ (tắt được trong Cài đặt launcher).
- **Hình nền online** từ nguồn miễn phí không cần khoá: ảnh trong ngày của Bing, của Wikimedia hoặc ảnh ngẫu nhiên
  Lorem Picsum. Tải tối đa một lần mỗi ngày.
- **Bảo vệ màn hình** với hình nền trong ngày và đồng hồ di chuyển quanh màn hình.
- **Cập nhật từ GitHub**: "Kiểm tra cập nhật" trong Cài đặt launcher cài bản phát hành mới nhất của repo này.
- Cài đặt launcher là thẻ Liquid Glass gọn trên nền Home đã làm mờ; nút Settings là biểu tượng bánh răng.
- Chỉ có tiếng Anh và tiếng Việt.

## Mạng và quyền riêng tư

Launcher gốc không dùng mạng. Bản này dùng mạng ở đúng một chỗ (`WeatherFeed.java`) và khai báo quyền `INTERNET`
cho việc đó:

- Thời tiết: `api.open-meteo.com`, `geocoding-api.open-meteo.com`, và `ipwho.is` khi chưa đặt thành phố. Chỉ chạy
  khi màn hình chính đang hiện, tối đa 30 phút một lần.
- Hình nền online (chỉ khi bạn chọn nguồn): `www.bing.com`, `api.wikimedia.org` và `upload.wikimedia.org`, hoặc
  `picsum.photos`; tối đa một lần mỗi ngày.
- Cập nhật: `api.github.com` và đường dẫn tải bản phát hành, chỉ khi bạn bấm "Kiểm tra cập nhật". Bản cập nhật được
  giao cho trình cài đặt của Android, nó sẽ hỏi bạn xác nhận, và phải ký bằng cùng khoá với app đang cài.
- Để khỏi tra theo IP, đặt `weather_city` (ví dụ `Hai Phong`) trong
  `app/src/main/res/values/config.xml`.
- Tắt thời tiết trong Cài đặt launcher, hoặc đặt `show_weather` là `false`.

## Bảo vệ màn hình

Chọn GRAPE2TV trong cài đặt Bảo vệ màn hình của TV (Cài đặt launcher có lối tắt), hoặc từ máy tính:

```
adb shell settings put secure screensaver_components com.grape2tv.home/com.gathin.tvhome.GrapeDream
```

## Đang phát: cấp quyền truy cập thông báo

Android chỉ cho app liệt kê phiên phát nhạc của app khác khi có quyền truy cập thông báo. GRAPE2TV chỉ đọc phiên
phát nhạc, không đọc thông báo. Hãy cấp quyền trong cài đặt "Truy cập thông báo" của TV, hoặc dùng adb:

```
adb shell cmd notification allow_listener com.grape2tv.home/com.gathin.tvhome.GuardRevival
```

Nếu TV không có mục đó, bấm dòng "Điều khiển nhạc đang phát" trong Cài đặt launcher để xem lại lệnh này.

## Build

1. Mở thư mục này (thư mục có `settings.gradle`) bằng Android Studio Rabbit 1.
2. Đợi Gradle sync. Dự án dùng Android Gradle Plugin 9.4.0, Gradle 9.6.0 và compileSdk 34.
3. Build > Generate Signed App Bundle / APK, rồi ký bằng keystore của bạn.

Phát hành bản cập nhật: tăng `versionCode` và `versionName` trong `app/build.gradle`, build APK ký bằng cùng
keystore, rồi đính kèm vào một bản phát hành GitHub có tag là phiên bản mới (ví dụ `v1.2.0`).

Application id là `com.grape2tv.home` (đổi bằng `launcherApplicationId` trong `gradle.properties`). Package Java
vẫn là `com.gathin.tvhome` để dễ gộp thay đổi từ bản gốc.

## Tình trạng

Phần mã Android thêm vào được viết khi không có Android SDK để biên dịch và chưa được chạy thử trên TV. Hãy coi
lần build đầu là bản thử và báo lỗi nếu gặp.

## Giấy phép

MIT, xem [`LICENSE`](LICENSE): Copyright (c) 2026 Gathin cho CleanYourTV, cùng các thay đổi của GRAPE2TV. Phông
Outfit theo SIL Open Font License, xem `FONT-LICENSE-OFL.txt`.
