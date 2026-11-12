package bd.bangla.browser;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-app downloader. Keeps cookies and the user agent (so sites that refuse
 * plain DownloadManager requests still serve the file) and reports live size,
 * remaining bytes and speed to the downloads screen. Nothing blocks the UI.
 */
final class Downloader {

    private Downloader() { }

    static final int REQ_STORAGE = 4302;

    static final class Task {
        final String name;
        final String mime;
        long total;
        long done;
        long speed;
        volatile boolean stop;
        String phase = "সংযোগ হচ্ছে…";

        Task(String name, String mime) {
            this.name = name;
            this.mime = mime == null ? "" : mime;
        }

        int percent() {
            if (total <= 0) return 0;
            return (int) Math.min(100, done * 100 / total);
        }
    }

    private static final List<Task> ACTIVE =
            Collections.synchronizedList(new ArrayList<Task>());

    static List<Task> active() {
        synchronized (ACTIVE) {
            return new ArrayList<>(ACTIVE);
        }
    }

    static int activeCount() {
        return ACTIVE.size();
    }

    static void cancel(Task t) {
        if (t != null) t.stop = true;
    }

    static String sizeText(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
        }
        return String.format(java.util.Locale.US, "%.2f GB", bytes / 1073741824.0);
    }

    static void start(final MainActivity act, final String url, final String ua,
                      final String contentDisposition, final String mime, final long len) {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29
                && act.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQ_STORAGE);
            Toast.makeText(act, "ফাইল রাখতে স্টোরেজের অনুমতি দাও, তারপর আবার চাপো",
                    Toast.LENGTH_LONG).show();
            return;
        }

        final Task task = new Task(fileName(url, contentDisposition, mime), mime);
        task.total = len > 0 ? len : 0;
        ACTIVE.add(task);
        Toast.makeText(act, "ডাউনলোড শুরু হয়েছে: " + task.name, Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            public void run() {
                runDownload(act, url, ua, mime, task);
            }
        }).start();
    }

    private static void runDownload(MainActivity act, String url, String ua,
                                    String mime, Task task) {
        HttpURLConnection cn = null;
        Uri publicUri = null;
        File file = null;
        String fail = null;

        try {
            cn = (HttpURLConnection) new URL(url).openConnection();
            cn.setInstanceFollowRedirects(true);
            cn.setConnectTimeout(20000);
            cn.setReadTimeout(30000);
            cn.setRequestProperty("User-Agent", ua == null || ua.length() == 0
                    ? "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/122 Mobile Safari/537.36"
                    : ua);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && cookie.length() > 0) {
                cn.setRequestProperty("Cookie", cookie);
            }
            String ref = act.refererFor(url);
            if (ref != null) cn.setRequestProperty("Referer", ref);
            cn.setRequestProperty("Accept", "*/*");

            int code = cn.getResponseCode();
            if (code < 200 || code >= 400) {
                fail = "সার্ভার ফাইলটা দিচ্ছে না (" + code + ")";
            }

            String cl = cn.getHeaderField("Content-Length");
            if (cl != null) {
                try {
                    long parsed = Long.parseLong(cl.trim());
                    if (parsed > 0) task.total = parsed;
                } catch (Exception ignored) { }
            }

            if (fail == null) {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put("_display_name", task.name);
                    cv.put("mime_type", task.mime.length() == 0
                            ? "application/octet-stream" : task.mime);
                    cv.put("relative_path", Environment.DIRECTORY_DOWNLOADS);
                    cv.put("is_pending", 1);
                    ContentResolver cr = act.getContentResolver();
                    publicUri = cr.insert(Uri.parse("content://media/external/downloads"), cv);
                    if (publicUri == null) {
                        fail = "ফোনে ফাইল রাখার জায়গা পাওয়া যায়নি";
                    }
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    file = new File(dir, task.name);
                    int n = 1;
                    while (file.exists()) {
                        file = new File(dir, numbered(task.name, n++));
                    }
                }
            }

            if (fail == null) {
                OutputStream out = null;
                InputStream in = null;
                try {
                    out = publicUri != null
                            ? act.getContentResolver().openOutputStream(publicUri)
                            : new FileOutputStream(file);
                    if (out == null) {
                        fail = "ফাইল লেখা যাচ্ছে না";
                    } else {
                        in = cn.getInputStream();
                        byte[] buf = new byte[16384];
                        long lastTime = System.currentTimeMillis();
                        long lastBytes = 0;
                        int read;
                        task.phase = "চলছে";
                        while (!task.stop && (read = in.read(buf)) > 0) {
                            out.write(buf, 0, read);
                            task.done += read;
                            long now = System.currentTimeMillis();
                            if (now - lastTime >= 400) {
                                task.speed = (task.done - lastBytes) * 1000L
                                        / Math.max(1, now - lastTime);
                                lastBytes = task.done;
                                lastTime = now;
                            }
                        }
                        out.flush();
                    }
                } finally {
                    try { if (in != null) in.close(); } catch (Exception ignored) { }
                    try { if (out != null) out.close(); } catch (Exception ignored) { }
                }
            }
        } catch (Exception e) {
            fail = "নেটওয়ার্ক সমস্যা, ফাইলটা নামানো যায়নি";
        } finally {
            try { if (cn != null) cn.disconnect(); } catch (Exception ignored) { }
        }

        if (task.stop) {
            ACTIVE.remove(task);
            deleteTarget(act, publicUri, file);
            toast(act, "ডাউনলোড বাতিল হয়েছে");
            return;
        }

        if (fail != null) {
            ACTIVE.remove(task);
            deleteTarget(act, publicUri, file);
            toast(act, fail);
            return;
        }

        if (publicUri != null) {
            try {
                ContentValues cv = new ContentValues();
                cv.put("is_pending", 0);
                act.getContentResolver().update(publicUri, cv, null, null);
            } catch (Exception ignored) { }
        }

        Uri record = publicUri;
        if (record == null && file != null) record = Uri.fromFile(file);
        act.addDownloadRecord(task.name, record == null ? "" : record.toString(),
                task.mime, task.done);
        ACTIVE.remove(task);
        toast(act, "ডাউনলোড সম্পন্ন: " + task.name + " · " + sizeText(task.done));
    }

    private static void toast(final MainActivity act, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                try {
                    Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) { }
            }
        });
    }

    private static void deleteTarget(MainActivity act, Uri uri, File file) {
        try {
            if (uri != null) act.getContentResolver().delete(uri, null, null);
        } catch (Exception ignored) { }
        try {
            if (file != null) file.delete();
        } catch (Exception ignored) { }
    }

    private static String numbered(String name, int n) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return name + " (" + n + ")";
        return name.substring(0, dot) + " (" + n + ")" + name.substring(dot);
    }

    static String fileName(String url, String cd, String mime) {
        String name = null;
        try {
            if (cd != null) {
                int i = cd.toLowerCase(java.util.Locale.US).indexOf("filename=");
                if (i >= 0) {
                    name = cd.substring(i + 9).trim();
                    if (name.startsWith("\"")) {
                        name = name.substring(1);
                        int q = name.indexOf('"');
                        if (q > 0) name = name.substring(0, q);
                    } else {
                        int sc = name.indexOf(';');
                        if (sc > 0) name = name.substring(0, sc).trim();
                    }
                    try {
                        name = URLDecoder.decode(name, "UTF-8");
                    } catch (Exception ignored) { }
                }
            }
            if (name == null || name.length() == 0) {
                String path = Uri.parse(url).getLastPathSegment();
                if (path != null) name = path;
            }
        } catch (Exception ignored) { }
        if (name == null || name.length() == 0) name = "download";
        name = name.replace("/", "_").replace("\\", "_").replace("..", "_");
        if (name.length() > 90) {
            String ext = "";
            int dot = name.lastIndexOf('.');
            if (dot > 0) ext = name.substring(dot);
            name = name.substring(0, 80) + ext;
        }
        if (name.indexOf('.') < 0) {
            String m = mime == null ? "" : mime.toLowerCase(java.util.Locale.US);
            if (m.contains("pdf")) name += ".pdf";
            else if (m.startsWith("image/")) name += ".jpg";
            else if (m.startsWith("video/")) name += ".mp4";
            else if (m.startsWith("audio/")) name += ".mp3";
            else if (m.contains("zip")) name += ".zip";
        }
        return name;
    }
}
