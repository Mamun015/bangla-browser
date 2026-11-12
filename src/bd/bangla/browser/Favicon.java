package bd.bangla.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads a site's own icon for history, bookmarks and the favourite sites list. */
final class Favicon {

    private static final android.util.LruCache<String, Bitmap> MEM =
            new android.util.LruCache<>(120);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private Favicon() { }

    static String hostOf(String url) {
        try {
            String h = Uri.parse(url).getHost();
            if (h == null) return "";
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) {
            return "";
        }
    }

    static void bind(final Context c, final String pageUrl, final ImageView view,
                     final int tileColor, final int textColor) {
        final String host = hostOf(pageUrl);
        view.setTag(host);
        Bitmap cached = MEM.get(host);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        final Context app = c.getApplicationContext();
        File disk = new File(dir(app), host + ".png");
        if (host.length() > 0 && disk.exists()) {
            Bitmap d = BitmapFactory.decodeFile(disk.getAbsolutePath());
            if (d != null) {
                MEM.put(host, d);
                view.setImageBitmap(d);
                return;
            }
        }
        view.setImageBitmap(letter(host, tileColor, textColor));
        if (host.length() == 0) return;
        POOL.execute(new Runnable() {
            public void run() {
                final Bitmap icon = fetch(host);
                if (icon == null) return;
                save(dir(app), host, icon);
                UI.post(new Runnable() {
                    public void run() {
                        MEM.put(host, icon);
                        if (host.equals(view.getTag())) view.setImageBitmap(icon);
                    }
                });
            }
        });
    }

    private static File dir(Context c) {
        File d = new File(c.getCacheDir(), "favicons");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static void save(File dir, String host, Bitmap icon) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(new File(dir, host + ".png"));
            icon.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) { }
        }
    }

    private static Bitmap fetch(String host) {
        HttpURLConnection cn = null;
        try {
            String url = "https://www.google.com/s2/favicons?sz=64&domain="
                    + URLEncoder.encode(host, "UTF-8");
            cn = (HttpURLConnection) new URL(url).openConnection();
            cn.setConnectTimeout(6000);
            cn.setReadTimeout(6000);
            cn.setRequestProperty("User-Agent", "Mozilla/5.0");
            InputStream in = cn.getInputStream();
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        } finally {
            try { if (cn != null) cn.disconnect(); } catch (Exception ignored) { }
        }
    }

    static Bitmap letter(String host, int bg, int fg) {
        int size = 96;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int color = bg;
        if (host == null || host.length() == 0) {
            p.setColor(Color.parseColor("#9AA5A0"));
        } else {
            p.setColor(color);
        }
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p);

        String ch = host == null || host.length() == 0
                ? "?" : host.substring(0, 1).toUpperCase(java.util.Locale.US);
        p.setColor(fg);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(size * 0.52f);
        Rect r = new Rect();
        p.getTextBounds(ch, 0, ch.length(), r);
        cv.drawText(ch, size / 2f - r.exactCenterX(), size / 2f - r.exactCenterY(), p);
        return b;
    }
}
