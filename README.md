# GRAPE2TV

A launcher for Android TV and Google TV, with a clean home screen, local weather, quick controls for the music
playing in the background, and a choice of interface styles: the stock look, **Liquid Glass** (Apple-style) and
**Sony Xperia**.

Read in: English · [Tiếng Việt](README.vi.md)

GRAPE2TV is a fork of [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin (MIT licence). The
debloat scripts, themes and device notes of the original project are not included in this repository.

## What this fork adds

- **Local weather** in the top bar: icon, temperature, condition, city and the day's low and high. Data comes
  from [Open-Meteo](https://open-meteo.com) (no account or key).
- **Now playing**: a pill in the top bar shows the title and artist of the app playing in the background. OK opens
  a panel with artwork, progress, previous, play or pause, next and "open the app".
- **Interface styles**, chosen in Launcher settings (the Settings button in the top bar): Default, Liquid Glass
  (translucent tiles, a light-bending rim, rounder corners) and Sony Xperia (flat, black, rounded squares).
- English and Vietnamese only.

## Network and privacy

The upstream launcher never touches the network. This fork does, in one place (`WeatherFeed.java`), and declares
the `INTERNET` permission for it:

- Requests go to `api.open-meteo.com` and `geocoding-api.open-meteo.com`, and to `ipwho.is` when no city is set.
- They run only while Home is on screen, at most every 30 minutes.
- To avoid the IP lookup, set `weather_city` (for example `Hai Phong`) in
  `app/src/main/res/values/config.xml`.
- Switch the weather off in Launcher settings, or set `show_weather` to `false`.

## Now playing: allow notification access

Android only lets an app list other apps' media sessions when it has notification access. GRAPE2TV reads media
sessions only; it does not read notifications. Allow it in the TV's notification access settings, or with adb:

```
adb shell cmd notification allow_listener com.grape2tv.home/com.gathin.tvhome.GuardRevival
```

If the TV has no such setting, pressing the "Now playing controls" row in Launcher settings shows this command.

## Build

1. Open this folder (the one with `settings.gradle`) in Android Studio Rabbit 1.
2. Let Gradle sync. The project uses Android Gradle Plugin 9.4.0, Gradle 9.6.0 and compileSdk 34.
3. Build > Generate Signed App Bundle / APK, and sign with your own keystore.

The application id is `com.grape2tv.home` (change it with `launcherApplicationId` in `gradle.properties`). The
Java package stays `com.gathin.tvhome`, so merging changes from upstream remains easy.

## Status

The Android code of the additions was written without an Android SDK to compile against and has not been run on
a TV yet. Treat the first build as a test build and report problems.

## Licence

MIT, see [`LICENSE`](LICENSE): Copyright (c) 2026 Gathin for CleanYourTV, plus the GRAPE2TV changes. The Outfit
typeface is under the SIL Open Font License, see `FONT-LICENSE-OFL.txt`.
