package bd.bangla.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String HOME_URL = "file:///android_asset/home.html";
    private static final String PREFS = "bangla_browser";
    private static final String UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private WebView web;
    private EditText urlBar;
    private ProgressBar progress;
    private TextView btnBack, btnForward, btnStar, pageTitle;
    private LinearLayout rootView, topBar, addrPill, bottomBar;
    private SharedPreferences prefs;

    private boolean darkMode, desktopMode;
    private String uaMobile;
    private int cBg, cSurface, cText, cMuted, cAccent, cLine;
    private final List<TextView> glyphs = new ArrayList<>();
    private final List<TextView> labels = new ArrayList<>();
    private long lastBackPress = 0;

    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_FILE = 4201;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        darkMode = prefs.getBoolean("dark", false);
        desktopMode = prefs.getBoolean("desktop", false);
        pickColors();

        if (Build.VERSION.SDK_INT >= 21) {
            Window w = getWindow();
            w.setStatusBarColor(cSurface);
            w.setNavigationBarColor(cBg);
            if (Build.VERSION.SDK_INT >= 23 && !darkMode) {
                w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(cBg));

        buildUi();
        createWebView();

        String restore = savedInstanceState != null ? savedInstanceState.getString("url") : null;
        if (restore != null && !restore.equals("about:blank") && !restore.startsWith("data:")) {
            web.loadUrl(restore);
        } else {
            loadHome();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null && web.getUrl() != null) out.putString("url", web.getUrl());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getData() != null && web != null) {
            web.loadUrl(intent.getData().toString());
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 2000) {
            super.onBackPressed();
        } else {
            lastBackPress = now;
            toast("বের হতে আবার চাপুন");
        }
    }

    // ---------------------------------------------------------------- colors

    private void pickColors() {
        if (darkMode) {
            cBg = Color.parseColor("#0F1211");
            cSurface = Color.parseColor("#191E1C");
            cText = Color.parseColor("#E9EFEC");
            cMuted = Color.parseColor("#8B9791");
            cAccent = Color.parseColor("#2BBD8F");
            cLine = Color.parseColor("#252C29");
        } else {
            cBg = Color.parseColor("#FFFFFF");
            cSurface = Color.parseColor("#F1F5F3");
            cText = Color.parseColor("#111714");
            cMuted = Color.parseColor("#68736D");
            cAccent = Color.parseColor("#006A4E");
            cLine = Color.parseColor("#E1E8E4");
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable roundedBorder(int color, float radius) {
        GradientDrawable d = rounded(color, radius);
        d.setStroke(dp(1), cLine);
        return d;
    }

    private void ripple(View v) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        if (tv.resourceId != 0) v.setBackgroundResource(tv.resourceId);
    }

    // ---------------------------------------------------------------- ui

    private void buildUi() {
        rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setBackgroundColor(cBg);
        rootView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- top bar
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(cBg);
        topBar.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(this);
        badge.setText("বাংলা ব্রাউজার");
        badge.setTextSize(13);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(cAccent);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleRow.addView(badge, blp);

        pageTitle = new TextView(this);
        pageTitle.setTextSize(11);
        pageTitle.setTextColor(cMuted);
        pageTitle.setSingleLine(true);
        pageTitle.setGravity(Gravity.END);
        pageTitle.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f);
        titleRow.addView(pageTitle, plp);

        topBar.addView(titleRow);

        // ---- address pill
        addrPill = new LinearLayout(this);
        addrPill.setOrientation(LinearLayout.HORIZONTAL);
        addrPill.setGravity(Gravity.CENTER_VERTICAL);
        addrPill.setBackground(roundedBorder(cSurface, 24));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl.topMargin = dp(8);

        TextView lock = new TextView(this);
        lock.setText("🔒");
        lock.setTextSize(12);
        lock.setPadding(dp(14), 0, dp(6), 0);
        lock.setTextColor(cMuted);
        addrPill.addView(lock);

        urlBar = new EditText(this);
        urlBar.setSingleLine(true);
        urlBar.setTextSize(14);
        urlBar.setTextColor(cText);
        urlBar.setHintTextColor(cMuted);
        urlBar.setHint("সার্চ করুন বা ঠিকানা লিখুন");
        urlBar.setBackground(null);
        urlBar.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        urlBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBar.setPadding(0, dp(9), 0, dp(9));
        urlBar.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        addrPill.addView(urlBar, ulp);

        urlBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                        || actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    go(urlBar.getText().toString());
                    return true;
                }
                return false;
            }
        });

        TextView reload = new TextView(this);
        reload.setText("↻");
        reload.setTextSize(19);
        reload.setTextColor(cMuted);
        reload.setPadding(dp(10), dp(4), dp(16), dp(4));
        ripple(reload);
        reload.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                web.reload();
            }
        });
        addrPill.addView(reload);

        topBar.addView(addrPill, pl);
        rootView.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- progress
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        if (Build.VERSION.SDK_INT >= 21) progress.setProgressTintList(ColorStateList.valueOf(cAccent));
        LinearLayout.LayoutParams prlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        rootView.addView(progress, prlp);

        // ---- web holder
        FrameLayout holder = new FrameLayout(this);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rootView.addView(holder, hlp);
        this.holder = holder;

        // ---- bottom bar
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(cBg);
        bottomBar.setPadding(dp(4), dp(4), dp(4), dp(6));

        btnBack = navItem("◀", "পিছনে", new Runnable() {
            public void run() { if (web.canGoBack()) web.goBack(); }
        });
        btnForward = navItem("▶", "সামনে", new Runnable() {
            public void run() { if (web.canGoForward()) web.goForward(); }
        });
        navItem("⌂", "হোম", new Runnable() {
            public void run() { loadHome(); }
        });
        btnStar = navItem("☆", "বুকমার্ক", new Runnable() {
            public void run() { toggleBookmark(); }
        });
        navItem("⋮", "মেনু", new Runnable() {
            public void run() { showMenu(); }
        });

        LinearLayout.LayoutParams bblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rootView.addView(bottomBar, bblp);

        setContentView(rootView);
        updateNavButtons();
    }

    private FrameLayout holder;

    private TextView navItem(String glyph, String label, final Runnable action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, dp(6), 0, dp(2));
        ripple(box);
        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });

        TextView g = new TextView(this);
        g.setText(glyph);
        g.setTextSize(18);
        g.setGravity(Gravity.CENTER);
        g.setTextColor(cText);
        box.addView(g);
        glyphs.add(g);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(9);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(cMuted);
        t.setPadding(0, dp(2), 0, 0);
        box.addView(t);
        labels.add(t);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bottomBar.addView(box, lp);
        return g;
    }

    private void updateNavButtons() {
        if (web == null) return;
        btnBack.setAlpha(web.canGoBack() ? 1f : 0.3f);
        btnForward.setAlpha(web.canGoForward() ? 1f : 0.3f);
        btnStar.setText(isBookmarked(web.getUrl()) ? "★" : "☆");
        btnStar.setTextColor(isBookmarked(web.getUrl()) ? cAccent : cText);
    }

    // ---------------------------------------------------------------- webview

    private void createWebView() {
        web = new WebView(this);
        web.setBackgroundColor(cBg);
        web.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        holder.addView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        uaMobile = s.getUserAgentString().replace("; wv", "");
        s.setUserAgentString(desktopMode ? UA_DESKTOP : uaMobile);

        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                                                   android.webkit.WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap fav) {
                progress.setVisibility(View.VISIBLE);
                if (isRealPage(url)) urlBar.setText(prettyUrl(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                updateNavButtons();
                if (url != null && url.startsWith(HOME_URL)) {
                    injectRecent();
                }
            }

            @Override
            public void onReceivedError(WebView view, int code, String desc, String failingUrl) {
                if (!isRealPage(failingUrl)) return;
                progress.setVisibility(View.GONE);
                showErrorPage(failingUrl);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null && !title.startsWith("file://")) {
                    pageTitle.setText(title.length() > 28 ? title.substring(0, 27) + "…" : title);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(Intent.createChooser(i, "ফাইল বাছুন"), REQ_FILE);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
            }
        });

        web.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try {
                    android.app.DownloadManager.Request req =
                            new android.app.DownloadManager.Request(Uri.parse(url));
                    req.setMimeType(mime);
                    req.setNotificationVisibility(
                            android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,
                            android.webkit.URLUtil.guessFileName(url, cd, mime));
                    ((android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                    toast("ডাউনলোড শুরু হয়েছে");
                } catch (Exception e) {
                    toast("ডাউনলোড করা যায়নি");
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (filePathCallback != null) {
                Uri[] result = null;
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // ---------------------------------------------------------------- navigation

    private boolean isRealPage(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String prettyUrl(String url) {
        String u = url;
        if (u.startsWith("https://")) u = u.substring(8);
        else if (u.startsWith("http://")) u = u.substring(7);
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private boolean handleUrl(String url) {
        if (url == null) return true;
        if (url.startsWith("bangla://")) {
            internal(url);
            return true;
        }
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) { }
        return true;
    }

    private void internal(String url) {
        Uri u = Uri.parse(url);
        String host = u.getHost() == null ? "" : u.getHost();
        if (host.equals("search")) {
            String q = u.getQueryParameter("q");
            if (q != null && q.trim().length() > 0) {
                web.loadUrl("https://www.google.com/search?q=" + enc(q.trim()));
                addHistory("গুগল সার্চ", "https://www.google.com/search?q=" + enc(q.trim()));
            }
        } else if (host.equals("reload")) {
            web.reload();
        } else if (host.equals("open")) {
            String s = u.getQueryParameter("u");
            if (s != null) {
                String target = s;
                if (!target.startsWith("http")) target = "https://" + target;
                web.loadUrl(target);
            }
        }
    }

    private void go(String input) {
        String t = input == null ? "" : input.trim();
        if (t.length() == 0) return;
        hideKeyboard();
        if (t.startsWith("bangla://")) {
            internal(t);
            return;
        }
        if (t.contains(" ") || !t.contains(".")) {
            String search = "https://www.google.com/search?q=" + enc(t);
            web.loadUrl(search);
            addHistory(t, search);
            return;
        }
        if (!t.startsWith("http://") && !t.startsWith("https://")) t = "https://" + t;
        web.loadUrl(t);
    }

    private String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
            urlBar.clearFocus();
        } catch (Exception ignored) { }
    }

    private void loadHome() {
        web.loadUrl(HOME_URL);
    }

    private void showErrorPage(String url) {
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:" + (darkMode ? "#0F1211" : "#FFFFFF")
                + ";color:" + (darkMode ? "#E9EFEC" : "#111714") + ";padding:40px 24px;text-align:center}"
                + "h2{font-size:18px;margin:0 0 8px}p{font-size:13px;color:#8B9791;word-break:break-all}"
                + "a{display:inline-block;margin-top:20px;background:" + (darkMode ? "#2BBD8F" : "#006A4E")
                + ";color:#fff;text-decoration:none;padding:12px 22px;border-radius:14px;font-size:14px}</style>"
                + "<body><div style='font-size:40px'>📡</div><h2>পেজ লোড হয়নি</h2>"
                + "<p>ইন্টারনেট সংযোগ দেখুন, তারপর আবার চেষ্টা করুন।</p>"
                + "<p style='font-size:11px'>" + url + "</p>"
                + "<a href='bangla://reload'>আবার চেষ্টা করুন</a></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    // ---------------------------------------------------------------- bookmarks / history

    private List<String[]> loadList(String key) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new String[]{o.getString("t"), o.getString("u")});
            }
        } catch (Exception ignored) { }
        return out;
    }

    private void saveList(String key, List<String[]> list) {
        JSONArray arr = new JSONArray();
        for (String[] row : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("t", row[0]);
                o.put("u", row[1]);
                arr.put(o);
            } catch (Exception ignored) { }
        }
        prefs.edit().putString(key, arr.toString()).apply();
    }

    private boolean isBookmarked(String url) {
        if (url == null) return false;
        for (String[] row : loadList("bookmarks")) if (row[1].equals(url)) return true;
        return false;
    }

    private void addHistory(String title, String url) {
        List<String[]> h = loadList("history");
        for (int i = h.size() - 1; i >= 0; i--) if (h.get(i)[1].equals(url)) h.remove(i);
        h.add(0, new String[]{title == null || title.length() == 0 ? url : title, url});
        while (h.size() > 40) h.remove(h.size() - 1);
        saveList("history", h);
    }

    private void toggleBookmark() {
        String url = web.getUrl();
        if (url == null || url.startsWith("file://") || url.startsWith("data:")) {
            toast("এই পেজ বুকমার্ক করা যায় না");
            return;
        }
        List<String[]> b = loadList("bookmarks");
        for (int i = 0; i < b.size(); i++) {
            if (b.get(i)[1].equals(url)) {
                b.remove(i);
                saveList("bookmarks", b);
                updateNavButtons();
                toast("বুকমার্ক সরানো হয়েছে");
                return;
            }
        }
        String t = pageTitle.getText().toString();
        b.add(0, new String[]{t, url});
        saveList("bookmarks", b);
        updateNavButtons();
        toast("বুকমার্কে রাখা হয়েছে");
    }

    private void injectRecent() {
        if (web == null) return;
        JSONArray arr = new JSONArray();
        List<String[]> h = loadList("history");
        for (int i = 0; i < h.size() && i < 4; i++) {
            try {
                JSONObject o = new JSONObject();
                o.put("t", h.get(i)[0]);
                o.put("u", h.get(i)[1]);
                arr.put(o);
            } catch (Exception ignored) { }
        }
        web.evaluateJavascript("window.setRecent && window.setRecent(" + arr.toString() + ");", null);
    }

    // ---------------------------------------------------------------- dialogs

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this,
                darkMode ? android.R.style.Theme_Material_Dialog_Alert
                        : android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    private void showList(String title, String key, final boolean clearable) {
        final List<String[]> items = loadList(key);
        if (items.isEmpty()) {
            toast("এখনো কিছু নেই");
            return;
        }
        String[] names = new String[items.size()];
        for (int i = 0; i < items.size(); i++) names[i] = items.get(i)[0];
        AlertDialog.Builder b = dialog().setTitle(title)
                .setItems(names, (d, which) -> web.loadUrl(items.get(which)[1]));
        if (clearable) {
            b.setNegativeButton("সব মুছুন", (d, w) -> {
                saveList(key, new ArrayList<>());
                toast("মুছে ফেলা হয়েছে");
            });
        }
        b.setPositiveButton("বন্ধ", null).show();
    }

    private void showMenu() {
        final String[] items = {
                "হোম পেজ",
                "বুকমার্ক",
                "ইতিহাস",
                darkMode ? "দিনের মোড ☀" : "রাতের মোড ☾",
                desktopMode ? "মোবাইল সাইট" : "ডেস্কটপ সাইট",
                "অন্য ব্রাউজারে খুলুন",
                "অ্যাপ সম্পর্কে"
        };
        dialog().setTitle("মেনু").setItems(items, (d, which) -> {
            switch (which) {
                case 0: loadHome(); break;
                case 1: showList("বুকমার্ক", "bookmarks", true); break;
                case 2: showList("ইতিহাস", "history", true); break;
                case 3:
                    darkMode = !darkMode;
                    prefs.edit().putBoolean("dark", darkMode).apply();
                    recreate();
                    break;
                case 4:
                    desktopMode = !desktopMode;
                    prefs.edit().putBoolean("desktop", desktopMode).apply();
                    web.getSettings().setUserAgentString(desktopMode ? UA_DESKTOP : uaMobile);
                    web.reload();
                    toast(desktopMode ? "ডেস্কটপ সাইট চালু" : "মোবাইল সাইট চালু");
                    break;
                case 5: {
                    String u = web.getUrl();
                    if (u == null) break;
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                    } catch (Exception e) {
                        toast("ব্রাউজার পাওয়া যায়নি");
                    }
                    break;
                }
                case 6: about(); break;
            }
        }).setNegativeButton("বন্ধ", null).show();
    }

    private void about() {
        String msg = "বাংলা ব্রাউজার\nসংস্করণ ১.০\n\n"
                + "সহজ, দ্রুত আর বাংলা ভাষার ব্রাউজার।\n"
                + "মোবাইলেই বানানো — কোনো কম্পিউটার ছাড়া।\n\n"
                + "শর্টকাট: পিছনে বাটনে দুইবার চাপ দিলে অ্যাপ বন্ধ হবে।";
        dialog().setTitle("অ্যাপ সম্পর্কে")
                .setMessage(msg)
                .setPositiveButton("ঠিক আছে", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
