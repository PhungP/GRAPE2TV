# GRAPE2TV 1.1.1

Tiếng Việt · [English](#english)

## Có gì mới

- **Sửa lỗi build:** hai hàm `showWallpaperPicker()` trùng tên trong `LauncherSettingsActivity` làm build APK thất bại. Hàm chọn nguồn hình nền online nay có tên `showOnlineWallpaperPicker()`.
- **Thanh trên cùng:** thời tiết, nhạc đang phát, Wi-Fi và Cài đặt nay nằm sát mép trên màn hình, ngang hàng với đồng hồ, không còn bị đẩy xuống dưới lịch tháng.
- **Gọn hơn:** bỏ dòng chào ("Xin chào") và dòng thứ/ngày cạnh đồng hồ. Bật lại bằng `show_greeting` và `show_date` trong `config.xml`.
- **Nút Wi-Fi chỉ còn icon**, không còn tên mạng hay chữ "Mất mạng". Màu icon cho biết trạng thái: xanh là có mạng, cam là mạng không ra internet, đỏ là mất mạng (`wifi_icon_only`, `wifi_status_color`, `wifi_status_warnings`).
- **Icon mới:** chùm nho xanh cách điệu (icon thường, adaptive icon và banner TV).

## Nâng cấp

- Phiên bản `1.1.1`, `versionCode` 3.
- Ký bằng cùng keystore với bản cũ để cài đè được.

---

## English

## What's new

- **Build fix:** two methods named `showWallpaperPicker()` in `LauncherSettingsActivity` broke the APK build. The online-wallpaper source picker is now `showOnlineWallpaperPicker()`.
- **Top bar:** weather, now playing, Wi-Fi and Settings now sit at the top edge of the screen, level with the clock, instead of below the month calendar.
- **Cleaner header:** the greeting ("Hello") and the weekday/date text beside the clock are gone. Bring them back with `show_greeting` and `show_date` in `config.xml`.
- **Wi-Fi button is icon-only:** no network name or "Offline" text. The icon colour shows the state: green online, orange connected without internet, red offline (`wifi_icon_only`, `wifi_status_color`, `wifi_status_warnings`).
- **New icon:** a stylised green grape bunch (launcher icon, adaptive icon and TV banner).

## Upgrade

- Version `1.1.1`, `versionCode` 3.
- Sign with the same keystore as the previous build so it installs over it.
