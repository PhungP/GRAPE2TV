package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTPS helper shared by the update check, the online wallpapers and the downloads. Every call is made
 * from a short-lived background thread, on an action of the owner (a button in Launcher settings) or, for the
 * wallpaper, at most once a day while Home is on screen. The weather has its own, older copy (WeatherFeed).
 */
final class Net {
    static final String USER_AGENT = "GRAPE2TV/1.0 (+https://github.com/PhungP/GRAPE2TV)";
    private static final int TIMEOUT_MS = 12000;

    interface Progress {
        /** 0..100, from a background thread. */
        void onProgress(int percent);
    }

    private Net() {
    }

    private static HttpURLConnection open(String address, String accept) throws IOException {
        if (!address.startsWith("https://")) throw new IOException("https only");
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", USER_AGENT);
        if (accept != null) c.setRequestProperty("Accept", accept);
        return c;
    }

    /** The response body as text, at most 512 KB. */
    static String getString(String address, String accept) throws IOException {
        HttpURLConnection c = open(address, accept);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 512 * 1024) throw new IOException("response too large");
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    /** Streams the response into {@code dest} (written whole or not at all); returns the number of bytes. */
    static long download(String address, File dest, long maxBytes, Progress progress) throws IOException {
        HttpURLConnection c = open(address, null);
        File part = new File(dest.getPath() + ".part");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            long total = c.getContentLengthLong();
            if (total > maxBytes) throw new IOException("file too large");
            File dir = dest.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("no cache directory");
            long done = 0;
            int last = -1;
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (done > maxBytes) throw new IOException("file too large");
                    if (progress != null && total > 0) {
                        int p = (int) (done * 100 / total);
                        if (p != last) {
                            last = p;
                            progress.onProgress(p);
                        }
                    }
                }
            }
            if (dest.exists() && !dest.delete()) throw new IOException("cannot replace " + dest.getName());
            if (!part.renameTo(dest)) throw new IOException("cannot move the download");
            return done;
        } finally {
            c.disconnect();
            if (part.exists()) part.delete();
        }
    }
}
