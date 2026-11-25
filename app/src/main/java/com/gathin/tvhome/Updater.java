package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updates the launcher from the GitHub Releases of the repository in config.xml's update_repo.
 *
 * <p>Nothing here runs by itself: the owner presses "Check for updates" in Launcher settings. The check asks
 * GitHub's public API for the latest release, compares its tag (v1.2.0) with the installed versionName, and
 * only on the owner's confirmation downloads the release's .apk into the app's cache and hands it to Android's
 * package installer, which asks for its own confirmation. Android accepts the update only when the new APK is
 * signed with the same key and has a higher versionCode, so always sign releases with the same keystore.
 */
final class Updater {
    private static final Pattern REPO = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final long MAX_APK_BYTES = 80L * 1024 * 1024;

    static final class Release {
        final String tag;
        final String version;
        final String apkUrl;
        final String notes;

        Release(String tag, String apkUrl, String notes) {
            this.tag = tag;
            this.version = stripV(tag);
            this.apkUrl = apkUrl;
            this.notes = notes;
        }
    }

    interface CheckCallback {
        /** On the main thread: {@code release} is the latest one with an APK, or null; {@code failed} when it could not be asked. */
        void onChecked(Release release, boolean failed);
    }

    interface DownloadCallback {
        /** On the main thread. */
        void onProgress(int percent);

        void onDownloaded(File apk);

        void onFailed();
    }

    private Updater() {
    }

    // ---- pure helpers (unit tested) ------------------------------------------------------------------

    /** "v1.2.0" and "V1.2.0" to "1.2.0". */
    static String stripV(String tag) {
        if (tag == null) return "";
        String t = tag.trim();
        return t.startsWith("v") || t.startsWith("V") ? t.substring(1) : t;
    }

    /** Negative, zero or positive as {@code a} is older than, the same as or newer than {@code b}; missing parts count as 0. */
    static int compare(String a, String b) {
        int[] x = numbers(a), y = numbers(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    private static int[] numbers(String v) {
        Matcher m = Pattern.compile("\\d+").matcher(stripV(v));
        int[] out = new int[8];
        int n = 0;
        while (m.find() && n < out.length) {
            try {
                out[n++] = Integer.parseInt(m.group());
            } catch (NumberFormatException e) {
                out[n++] = Integer.MAX_VALUE;
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    // ---- the app ---------------------------------------------------------------------------------

    /** owner/name from config.xml, or "" when it is not set to something that looks like one. */
    static String repo(Context context) {
        String r = context.getString(R.string.update_repo).trim();
        return REPO.matcher(r).matches() ? r : "";
    }

    static String currentVersion(Context context) {
        try {
            String v = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    static void check(Context context, Handler main, CheckCallback callback) {
        final String repo = repo(context);
        Thread t = new Thread(() -> {
            Release release = null;
            boolean failed = false;
            try {
                if (repo.isEmpty()) throw new IOException("no repository set");
                String json = Net.getString("https://api.github.com/repos/" + repo + "/releases/latest",
                        "application/vnd.github+json");
                JSONObject root = new JSONObject(json);
                JSONArray assets = root.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        String name = a.optString("name", "");
                        String url = a.optString("browser_download_url", "");
                        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk") && url.startsWith("https://")) {
                            release = new Release(root.optString("tag_name", ""), url, root.optString("body", ""));
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                failed = true;
            }
            final Release r = release;
            final boolean f = failed;
            main.post(() -> callback.onChecked(r, f));
        }, "tvhome-update-check");
        t.setDaemon(true);
        t.start();
    }

    static void download(Context context, Release release, Handler main, DownloadCallback callback) {
        final File dir = new File(context.getCacheDir(), "update");
        Thread t = new Thread(() -> {
            try {
                File[] old = dir.listFiles();
                if (old != null) for (File f : old) f.delete();
                File apk = new File(dir, "GRAPE2TV-" + release.version + ".apk");
                Net.download(release.apkUrl, apk, MAX_APK_BYTES, p -> main.post(() -> callback.onProgress(p)));
                if (!looksLikeApk(apk)) throw new IOException("not an APK");
                main.post(() -> callback.onDownloaded(apk));
            } catch (IOException e) {
                main.post(callback::onFailed);
            }
        }, "tvhome-update-download");
        t.setDaemon(true);
        t.start();
    }

    /** An APK is a zip: it starts with "PK" and is more than a few bytes. */
    private static boolean looksLikeApk(File f) throws IOException {
        if (f.length() < 100 * 1024) return false;
        try (InputStream in = new FileInputStream(f)) {
            return in.read() == 'P' && in.read() == 'K';
        }
    }

    /** True when the owner has allowed this app to install packages ("install unknown apps"). */
    static boolean canInstall(Context context) {
        return context.getPackageManager().canRequestPackageInstalls();
    }

    /** Hands the APK to the package installer; the result comes to {@link UpdateReceiver}. */
    static void install(Context context, File apk) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setSize(apk.length());
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("grape2tv", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            }
            Intent result = new Intent(context, UpdateReceiver.class).setAction(UpdateReceiver.ACTION);
            PendingIntent pending = PendingIntent.getBroadcast(context, id, result,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            session.commit(pending.getIntentSender());
        }
    }
}
