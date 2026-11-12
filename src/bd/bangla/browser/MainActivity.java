package bd.bangla.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
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
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String HOME_URL = "file:///android_asset/home.html";
    private static final String PREFS = "bangla_browser";
    private boolean autoTranslate = true;
    private final java.util.HashMap<String, String> assetCache = new java.util.HashMap<String, String>();
    private static final String UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final int MAX_TABS = 32;

    private WebView current;
    private EditText urlBar;
    private ProgressBar progress;
    private TextView btnBack, btnForward, pageTitle, tabBadge, navTabLabel;
    private LinearLayout rootView, bottomBar;
    private FrameLayout screen, holder;
    private SharedPreferences prefs;
    private View overlay;

    private final List<Tab> tabs = new ArrayList<>();
    private int activeIndex = 0;

    private boolean darkMode, desktopMode, forceDark;
    private String uaMobile;
    private int cBg, cSurface, cText, cMuted, cAccent, cLine;
    private long lastBackPress = 0;

    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_FILE = 4201;
    private static final int REQ_ROLE = 4202;

    private View fullView;
    private WebChromeClient.CustomViewCallback fullCallback;

    private static class Tab {
        WebView web;
        String title = "";
        String url = "";
    }

    // ---------------------------------------------------------------- lifecycle

    // থিমের তিনটে রকম: ফোনের সেটিং মানা / সবসময় রাত / জোর করে সব কালো
    private static final String T_SYSTEM = "system", T_DARK = "dark", T_FORCE = "force";

    /** পুরনো ভার্সনের দুই সুইচ (dark / forcedark) থেকে থিম সেটিংয়ে উত্তরণ */
    private static String themeKey(SharedPreferences p) {
        String t = p.getString("theme", null);
        if (t == null) {
            t = p.getBoolean("forcedark", false) ? T_FORCE
                    : (p.getBoolean("dark", false) ? T_DARK : T_SYSTEM);
            p.edit().putString("theme", t).apply();
        }
        return t;
    }

    private String themeKey() {
        return themeKey(prefs);
    }

    private boolean themeIsDark() {
        String t = themeKey();
        return T_FORCE.equals(t) || T_DARK.equals(t) || (T_SYSTEM.equals(t) && systemDark);
    }

    private String themeName() {
        String t = themeKey();
        if (T_FORCE.equals(t)) return "জোর করে কালো";
        if (T_DARK.equals(t)) return "রাতের মোড";
        return "সিস্টেম সেটিং";
    }

    private void pickTheme() {
        final String[] names = {"সিস্টেম সেটিং অনুযায়ী", "রাতের মোড", "জোর করে কালো"};
        final String[] keys = {T_SYSTEM, T_DARK, T_FORCE};
        int checked = 0;
        String cur = themeKey();
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(cur)) checked = i;
        dialog().setTitle("থিম")
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    prefs.edit().putString("theme", keys[which]).apply();
                    d.dismiss();
                    recreate();
                })
                .show();
    }

    /** ফোনের সিস্টেম ডার্ক মোড চালু কি না — আসল কনফিগ থেকেই নেওয়া হয় */
    private boolean systemDark;

    @Override
    protected void attachBaseContext(Context base) {
        SharedPreferences p = base.getSharedPreferences(PREFS, MODE_PRIVATE);
        String t = themeKey(p);
        int baseMode = base.getResources().getConfiguration().uiMode;
        boolean sys = (baseMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean dark = T_FORCE.equals(t) || T_DARK.equals(t) || (T_SYSTEM.equals(t) && sys);
        boolean force = T_FORCE.equals(t);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        int mode = (dark && !force) ? Configuration.UI_MODE_NIGHT_YES
                                    : Configuration.UI_MODE_NIGHT_NO;
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode;
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        systemDark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        darkMode = themeIsDark();
        desktopMode = prefs.getBoolean("desktop", false);
        forceDark = T_FORCE.equals(themeKey());
        autoTranslate = prefs.getBoolean("autotranslate", true);
        pickColors();
        syncYouTubeTheme();

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

        buildShell();
        restoreSession();
    }

    @Override
    protected void onPause() {
        saveSession();
        super.onPause();
    }

    private void saveSession() {
        JSONArray arr = new JSONArray();
        for (Tab t : tabs) {
            try {
                JSONObject o = new JSONObject();
                String u = t.url;
                if (u == null || u.length() == 0 || u.equals("about:blank") || u.startsWith("data:")) {
                    u = HOME_URL;
                }
                if (u.startsWith(HOME_URL)) u = HOME_URL;
                o.put("u", u);
                arr.put(o);
            } catch (Exception ignored) { }
        }
        prefs.edit().putString("session", arr.toString())
                .putInt("session_index", activeIndex).apply();
    }

    private void restoreSession() {
        try {
            JSONArray arr = new JSONArray(prefs.getString("session", "[]"));
            if (arr.length() == 0) {
                newTab(homeUrl(), true);
                return;
            }
            int count = Math.min(arr.length(), MAX_TABS);
            for (int i = 0; i < count; i++) {
                String u = arr.getJSONObject(i).getString("u");
                if (u.length() == 0 || u.equals("about:blank") || u.startsWith("data:")) u = HOME_URL;
                if (u.startsWith(HOME_URL)) u = homeUrl();
                Tab t = new Tab();
                t.url = u;
                t.web = buildWebView();
                holder.addView(t.web, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                t.web.setVisibility(View.GONE);
                tabs.add(t);
            }
            int idx = prefs.getInt("session_index", 0);
            selectTab(Math.max(0, Math.min(idx, tabs.size() - 1)));
        } catch (Exception e) {
            newTab(homeUrl(), true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean dark = themeIsDark();
        boolean force = T_FORCE.equals(themeKey());
        boolean desk = prefs.getBoolean("desktop", false);
        if (dark != darkMode || force != forceDark) {
            recreate();
            return;
        }
        applySettings();
        if (desk != desktopMode) {
            desktopMode = desk;
            if (current != null) current.reload();
        }
        boolean tr = prefs.getBoolean("autotranslate", true);
        if (tr != autoTranslate) {
            autoTranslate = tr;
            if (current != null) current.reload();
        }
    }

    @Override
    protected void onDestroy() {
        exitFullscreen();
        for (Tab t : tabs) {
            if (t.web != null) {
                t.web.stopLoading();
                t.web.destroy();
            }
        }
        tabs.clear();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getData() != null) newTab(intent.getData().toString(), true);
    }

    @Override
    public void onBackPressed() {
        if (fullView != null) {
            exitFullscreen();
            return;
        }
        if (overlay != null) {
            closeOverlay();
            return;
        }
        if (current != null && current.canGoBack()) {
            current.goBack();
            return;
        }
        if (tabs.size() > 1) {
            closeTab(activeIndex);
            toast("ট্যাব বন্ধ হয়েছে");
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

    int dp(float v) {
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

    private GradientDrawable sheetBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(cBg);
        float r = dp(20);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return d;
    }

    private void ripple(View v) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        if (tv.resourceId != 0) v.setBackgroundResource(tv.resourceId);
    }

    private TextView glyphButton(String text, float size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        ripple(t);
        return t;
    }

    // ---------------------------------------------------------------- shell

    private void buildShell() {
        screen = new FrameLayout(this);
        screen.setBackgroundColor(cBg);

        rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setBackgroundColor(cBg);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(cBg);
        topBar.setPadding(dp(12), dp(8), dp(6), dp(8));

        // title row: brand | page title | tab badge | menu
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = new TextView(this);
        brand.setText("বাংলা ব্রাউজার");
        brand.setTextSize(13);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setTextColor(cAccent);
        titleRow.addView(brand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pageTitle = new TextView(this);
        pageTitle.setTextSize(11);
        pageTitle.setTextColor(cMuted);
        pageTitle.setSingleLine(true);
        pageTitle.setGravity(Gravity.END);
        pageTitle.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleRow.addView(pageTitle, plp);

        tabBadge = new TextView(this);
        tabBadge.setText("1");
        tabBadge.setTextSize(12);
        tabBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tabBadge.setTextColor(cText);
        tabBadge.setGravity(Gravity.CENTER);
        tabBadge.setBackground(roundedBorder(cSurface, 8));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(28), dp(28));
        tlp.rightMargin = dp(6);
        titleRow.addView(tabBadge, tlp);
        tabBadge.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showTabSwitcher(); }
        });

        // উপরের নাম-সারি বাদ: শুধু ঠিকানার বার আর নিচের বার

        // address row
        LinearLayout addrPill = new LinearLayout(this);
        addrPill.setOrientation(LinearLayout.HORIZONTAL);
        addrPill.setGravity(Gravity.CENTER_VERTICAL);
        addrPill.setBackground(roundedBorder(cSurface, 24));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView lock = new TextView(this);
        lock.setText("🔒");
        lock.setTextSize(12);
        lock.setPadding(dp(14), 0, dp(6), 0);
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
        addrPill.addView(urlBar, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

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

        TextView reload = glyphButton("↻", 19, cMuted);
        reload.setPadding(dp(12), dp(6), dp(14), dp(6));
        reload.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (current != null) current.reload(); }
        });
        addrPill.addView(reload);

        topBar.addView(addrPill, pl);
        rootView.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        if (Build.VERSION.SDK_INT >= 21) progress.setProgressTintList(ColorStateList.valueOf(cAccent));
        rootView.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        holder = new FrameLayout(this);
        rootView.addView(holder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(cBg);
        bottomBar.setPadding(dp(4), dp(4), dp(4), dp(6));

        btnBack = navItem("◀", "পিছনে", new Runnable() {
            public void run() { if (current != null && current.canGoBack()) current.goBack(); }
        });
        btnForward = navItem("▶", "সামনে", new Runnable() {
            public void run() { if (current != null && current.canGoForward()) current.goForward(); }
        });
        navItem("⌂", "হোম", new Runnable() {
            public void run() {
                if (current == null) return;
                urlBar.setText("");
                pageTitle.setText("");
                current.loadUrl(homeUrl());
            }
        });
        LinearLayout tabBox = navItemBox("▣", "ট্যাব", new Runnable() {
            public void run() { showTabSwitcher(); }
        });
        navTabLabel = (TextView) tabBox.getChildAt(1);
        navItem("⋮", "মেনু", new Runnable() {
            public void run() { showMenu(); }
        });

        rootView.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        screen.addView(rootView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(screen);
        updateNavButtons();
    }

    private TextView navItem(String glyph, String label, final Runnable action) {
        return (TextView) navItemBox(glyph, label, action).getChildAt(0);
    }

    private LinearLayout navItemBox(String glyph, String label, final Runnable action) {
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

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(9);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(cMuted);
        t.setPadding(0, dp(2), 0, 0);
        box.addView(t);

        bottomBar.addView(box, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return box;
    }

    private void updateNavButtons() {
        if (current == null) return;
        btnBack.setAlpha(current.canGoBack() ? 1f : 0.3f);
        btnForward.setAlpha(current.canGoForward() ? 1f : 0.3f);
        updateTabBadge();
    }

    private void updateTabBadge() {
        tabBadge.setText(String.valueOf(tabs.size()));
        if (navTabLabel != null) {
            navTabLabel.setText(tabs.size() > 1 ? "ট্যাব " + tabs.size() : "ট্যাব");
        }
    }

    // ---------------------------------------------------------------- tabs

    private Tab tabOf(WebView view) {
        for (Tab t : tabs) if (t.web == view) return t;
        return null;
    }

    private Tab active() {
        if (tabs.isEmpty()) return null;
        if (activeIndex >= tabs.size()) activeIndex = tabs.size() - 1;
        return tabs.get(activeIndex);
    }

    private void newTab(String url, boolean activate) {
        if (tabs.size() >= MAX_TABS) {
            toast("সর্বোচ্চ " + MAX_TABS + " ট্যাব খোলা যায়");
            return;
        }
        Tab t = new Tab();
        t.url = url;
        t.web = buildWebView();
        holder.addView(t.web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        t.web.setVisibility(View.GONE);
        tabs.add(t);
        if (activate) {
            selectTab(tabs.size() - 1);
        } else {
            t.web.loadUrl(url);
        }
        updateTabBadge();
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        activeIndex = index;
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).web.setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        Tab t = tabs.get(index);
        current = t.web;
        if (t.web.getUrl() == null && t.url != null && t.url.length() > 0) {
            t.web.loadUrl(t.url);
        }
        if (t.url != null && t.url.startsWith("file:")) {
            urlBar.setText("");
            urlBar.setHint("সার্চ করুন বা ঠিকানা লিখুন");
            pageTitle.setText("");
        } else {
            urlBar.setText(prettyUrl(t.url));
            pageTitle.setText(t.title);
        }
        updateNavButtons();
    }

    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        Tab t = tabs.get(index);
        holder.removeView(t.web);
        t.web.stopLoading();
        t.web.destroy();
        tabs.remove(index);
        if (tabs.isEmpty()) {
            newTab(homeUrl(), true);
            return;
        }
        if (activeIndex >= index) activeIndex = Math.max(0, activeIndex - 1);
        selectTab(activeIndex);
        updateTabBadge();
    }

    private void showTabSwitcher() {
        final FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(darkMode ? 0xF70F1211 : 0xF7FFFFFF);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(18), dp(16), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("ট্যাব (" + tabs.size() + ")");
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(cText);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView add = new TextView(this);
        add.setText("＋ নতুন ট্যাব");
        add.setTextSize(13);
        add.setTextColor(cAccent);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setPadding(dp(10), dp(8), dp(10), dp(8));
        ripple(add);
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeOverlay();
                newTab(homeUrl(), true);
            }
        });
        header.addView(add);

        TextView done = new TextView(this);
        done.setText("সম্পন্ন");
        done.setTextSize(13);
        done.setTextColor(cMuted);
        done.setPadding(dp(10), dp(8), dp(4), dp(8));
        ripple(done);
        done.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeOverlay(); }
        });
        header.addView(done);

        panel.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, 0);

        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            final Tab t = tabs.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(12), dp(8), dp(12));
            card.setBackground(roundedBorder(i == activeIndex ? cSurface : cBg, 14));

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = dp(8);

            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);

            TextView name = new TextView(this);
            String label = t.title;
            if (label == null || label.length() == 0) {
                label = t.url == null ? "নতুন ট্যাব" : prettyUrl(t.url);
                if (label.startsWith("file:")) label = "হোম";
            }
            name.setText(label);
            name.setTextSize(14);
            name.setTextColor(cText);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView(name);

            TextView addr = new TextView(this);
            addr.setText(t.url == null ? "" : t.url.replace("file:///android_asset/home.html", "হোম"));
            addr.setTextSize(11);
            addr.setTextColor(cMuted);
            addr.setSingleLine(true);
            addr.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addr.setPadding(0, dp(2), 0, 0);
            text.addView(addr);

            card.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView close = new TextView(this);
            close.setText("✕");
            close.setTextSize(15);
            close.setTextColor(cMuted);
            close.setPadding(dp(14), dp(8), dp(10), dp(8));
            ripple(close);
            close.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    closeOverlay();
                    closeTab(idx);
                }
            });
            card.addView(close);

            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    closeOverlay();
                    selectTab(idx);
                }
            });

            list.addView(card, clp);
        }

        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        box.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        openOverlay(box);
    }

    // ---------------------------------------------------------------- edge style menu

    private void showMenu() {
        final FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(0x66000000);
        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeOverlay(); }
        });

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(sheetBg());
        sheet.setPadding(dp(8), dp(8), dp(8), dp(20));
        sheet.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { }
        });

        LinearLayout grip = new LinearLayout(this);
        View bar = new View(this);
        bar.setBackground(rounded(cLine, 3));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(38), dp(4));
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        blp.topMargin = dp(6);
        blp.bottomMargin = dp(8);
        grip.addView(bar, blp);
        sheet.addView(grip);

        sheet.addView(sheetHeader());

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        list.addView(menuRow("➕", "নতুন ট্যাব", null, new Runnable() {
            public void run() { newTab(homeUrl(), true); }
        }));
        boolean marked = current != null && isBookmarked(current.getUrl());
        list.addView(menuRow(marked ? "★" : "☆",
                marked ? "বুকমার্ক সরান" : "এই পেজ বুকমার্ক করুন", null, new Runnable() {
                    public void run() { toggleBookmark(); }
                }));
        list.addView(menuRow("⭐", "বুকমার্ক", String.valueOf(loadList("bookmarks").size()), new Runnable() {
            public void run() { showList("বুকমার্ক", "bookmarks", true); }
        }));
        list.addView(menuRow("🕘", "ইতিহাস", String.valueOf(loadList("history").size()), new Runnable() {
            public void run() { showHistory(); }
        }));
        list.addView(menuRow("🏠", "প্রিয় সাইট", String.valueOf(loadSites().size()), new Runnable() {
            public void run() { showSiteEditor(); }
        }));
        int running = Downloader.activeCount();
        list.addView(menuRow("⬇️", "ডাউনলোড",
                running > 0 ? running + " চলছে"
                        : String.valueOf(downloadRecords().length()), new Runnable() {
            public void run() { showDownloads(); }
        }));
        list.addView(menuRow("📤", "শেয়ার", null, new Runnable() {
            public void run() {
                if (current == null) return;
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, current.getUrl());
                startActivity(Intent.createChooser(i, "শেয়ার করুন"));
            }
        }));
        list.addView(menuRow("🔗", "লিংক কপি", null, new Runnable() {
            public void run() { copyLink(); }
        }));

        list.addView(sectionLabel("পেজ"));

        list.addView(menuRow("🔍", "পেজে খুঁজুন", null, new Runnable() {
            public void run() { findInPage(); }
        }));
        list.addView(menuRow("🔄", "রিলোড", null, new Runnable() {
            public void run() { if (current != null) current.reload(); }
        }));
        list.addView(menuRow("🖨️", "প্রিন্ট", null, new Runnable() {
            public void run() { printPage(); }
        }));

        list.addView(toggleRow("🖥️", "ডেস্কটপ সাইট", desktopMode, new OnToggle() {
            public void onChange(boolean on) {
                desktopMode = on;
                prefs.edit().putBoolean("desktop", desktopMode).apply();
                applySettings();
                if (current != null) current.reload();
            }
        }));

        list.addView(menuRow(darkMode ? "🌙" : "☀️", "থিম", themeName(), new Runnable() {
            public void run() { pickTheme(); }
        }));

        list.addView(toggleRow("🅰️", "বাংলায় অনুবাদ", autoTranslate, new OnToggle() {
            public void onChange(boolean on) {
                autoTranslate = on;
                prefs.edit().putBoolean("autotranslate", autoTranslate).apply();
                if (current != null) current.reload();
            }
        }));

        list.addView(sectionLabel("ট্যাব"));

        list.addView(menuRow("🗑️", "সব ট্যাব বন্ধ", String.valueOf(tabs.size()), new Runnable() {
            public void run() { closeAllTabs(); }
        }));

        list.addView(sectionLabel("অ্যাপ"));

        list.addView(menuRow("🌐", "ডিফল্ট ব্রাউজার",
                isDefaultBrowser() ? "সেট আছে" : "সেট করুন", new Runnable() {
                    public void run() { askDefaultBrowser(); }
                }));

        list.addView(menuRow("⚙️", "সেটিংস", null, new Runnable() {
            public void run() {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        }));
        list.addView(menuRow("ℹ️", "অ্যাপ সম্পর্কে", "১.২.১.১২", new Runnable() {
            public void run() { about(); }
        }));

        scroll.addView(list);
        sheet.addView(scroll);

        // keep the sheet from growing past ~72% of the screen so it always fits
        final ScrollView menuScroll = scroll;
        final LinearLayout menuList = list;
        scroll.post(new Runnable() {
            public void run() {
                int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.72f);
                if (menuList.getHeight() > max) {
                    ViewGroup.LayoutParams lp = menuScroll.getLayoutParams();
                    lp.height = max;
                    menuScroll.setLayoutParams(lp);
                }
            }
        });

        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.BOTTOM;
        box.addView(sheet, slp);

        openOverlay(box);
        sheet.setTranslationY(dp(40));
        sheet.setAlpha(0f);
        sheet.animate().translationY(0).alpha(1f).setDuration(180).start();
    }

    private TextView sectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(cMuted);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(14), dp(14), dp(14), dp(6));
        return t;
    }

    private View menuRow(String icon, String label, String value, final Runnable action) {
        LinearLayout row = rowShell(icon, label, value);
        if (action != null) {
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    closeOverlay();
                    action.run();
                }
            });
        }
        return row;
    }

    private LinearLayout rowShell(String icon, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(12), dp(8));
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        if (tv.resourceId != 0) row.setBackgroundResource(tv.resourceId);

        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(rounded(cSurface, 11));
        TextView ico = new TextView(this);
        ico.setText(icon);
        ico.setTextSize(18);
        ico.setGravity(Gravity.CENTER);
        ico.setTextColor(cText);
        tile.addView(ico, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(tile, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(15);
        name.setTextColor(cText);
        name.setPadding(dp(14), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (value != null) {
            TextView val = new TextView(this);
            val.setText(value);
            val.setTextSize(12);
            val.setTextColor(cMuted);
            row.addView(val);
        }

        return row;
    }

    private interface OnToggle {
        void onChange(boolean checked);
    }

    /** সেটিংসের মতো আসল সুইচ: শিট বন্ধ বা রিফ্রেশ হয় না। */
    private View toggleRow(String icon, String label, boolean on, final OnToggle action) {
        final LinearLayout row = rowShell(icon, label, null);
        final Switch sw = new Switch(this);
        sw.setTag("bb_switch");
        sw.setChecked(on);
        if (Build.VERSION.SDK_INT >= 21) {
            sw.setThumbTintList(ColorStateList.valueOf(cAccent));
        }
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                action.onChange(checked);
            }
        });
        row.addView(sw);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sw.toggle(); }
        });
        return row;
    }

    private View sheetHeader() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(10), dp(4), dp(8), dp(12));

        ImageView logo = new ImageView(this);
        try {
            logo.setImageDrawable(getPackageManager().getApplicationIcon(getApplicationInfo()));
        } catch (Exception ignored) { }
        head.addView(logo, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);

        TextView name = new TextView(this);
        name.setText("বাংলা ব্রাউজার");
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(cText);
        texts.addView(name);

        TextView sub = new TextView(this);
        sub.setText("সংস্করণ ১.২.১.১২");
        sub.setTextSize(11);
        sub.setTextColor(cMuted);
        texts.addView(sub);

        head.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = glyphButton("✕", 16, cMuted);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeOverlay(); }
        });
        head.addView(close);
        return head;
    }

    // ---------------------------------------------------------------- downloads

    private JSONArray downloadRecords() {
        try {
            return new JSONArray(prefs.getString("downloads", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    void addDownloadRecord(String name, String uri, String mime, long size) {
        try {
            JSONArray old = downloadRecords();
            JSONObject o = new JSONObject();
            o.put("n", name);
            o.put("u", uri);
            o.put("m", mime);
            o.put("s", size);
            o.put("t", System.currentTimeMillis());
            JSONArray out = new JSONArray();
            out.put(o);
            for (int i = 0; i < old.length() && i < 99; i++) out.put(old.get(i));
            prefs.edit().putString("downloads", out.toString()).apply();
        } catch (Exception ignored) { }
    }

    String refererFor(String url) {
        try {
            if (current != null && isRealPage(current.getUrl())) return current.getUrl();
        } catch (Exception ignored) { }
        return null;
    }

    private static class ActiveRow {
        Downloader.Task task;
        ProgressBar bar;
        TextView detail;
    }

    private final java.util.List<ActiveRow> activeRows = new java.util.ArrayList<>();
    private final android.os.Handler dlHandler = new android.os.Handler(
            android.os.Looper.getMainLooper());
    private LinearLayout dlHost;

    private void showDownloads() {
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(cBg);

        LinearLayout panel = fullPanel("ডাউনলোড", new View.OnClickListener() {
            public void onClick(View v) {
                dialog().setTitle("তালিকা মুছবেন?")
                        .setMessage("শুধু তালিকা থেকে মুছে যাবে, ফোনে নামানো ফাইল থাকবে।")
                        .setPositiveButton("মুছুন", (d, w) -> {
                            prefs.edit().putString("downloads", "[]").apply();
                            closeOverlay();
                            showDownloads();
                        })
                        .setNegativeButton("না", null).show();
            }
        });

        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(6), 0, dp(6), dp(20));
        scroll.addView(rows);

        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        box.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        openOverlay(box);

        renderDownloads(rows);
        startDownloadTicker(rows);
    }

    private void renderDownloads(LinearLayout host) {
        host.removeAllViews();
        activeRows.clear();

        java.util.List<Downloader.Task> active = Downloader.active();
        if (!active.isEmpty()) {
            host.addView(sectionLabel("চলছে (" + active.size() + ")"));
            for (Downloader.Task t : active) host.addView(activeRow(t));
        }

        JSONArray arr = downloadRecords();
        if (arr.length() > 0) {
            host.addView(sectionLabel("সম্পন্ন"));
            for (int i = 0; i < arr.length() && i < 100; i++) {
                try {
                    JSONObject o = arr.getJSONObject(i);
                    host.addView(recordRow(o.optString("n", "ফাইল"),
                            o.optString("u", ""), o.optString("m", ""),
                            o.optLong("s", 0)));
                } catch (Exception ignored) { }
            }
        }

        if (active.isEmpty() && arr.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("এখনো কোনো ডাউনলোড নেই।\nকোনো পেজ থেকে ফাইল নামালে সেটা এখানে দেখা যাবে।");
            empty.setTextSize(13);
            empty.setTextColor(cMuted);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(60), dp(16), dp(20));
            host.addView(empty);
        }
    }

    private View activeRow(final Downloader.Task task) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(task.name);
        name.setTextSize(14);
        name.setTextColor(cText);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        top.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView cancel = glyphButton("✕", 14, cMuted);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Downloader.cancel(task);
                toast("ডাউনলোড বাতিল হচ্ছে");
            }
        });
        top.addView(cancel);
        box.addView(top);

        ProgressBar bar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        if (Build.VERSION.SDK_INT >= 21) {
            bar.setProgressTintList(ColorStateList.valueOf(cAccent));
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        blp.topMargin = dp(9);
        box.addView(bar, blp);

        TextView detail = new TextView(this);
        detail.setTextSize(11.5f);
        detail.setTextColor(cMuted);
        detail.setPadding(0, dp(7), 0, 0);
        detail.setText(downloadLine(task));
        box.addView(detail);

        ActiveRow holder = new ActiveRow();
        holder.task = task;
        holder.bar = bar;
        holder.detail = detail;
        activeRows.add(holder);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.setMargins(dp(6), dp(2), dp(6), dp(6));
        wrap.setBackground(roundedBorder(cSurface, 13));
        wrap.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.setLayoutParams(wlp);
        return wrap;
    }

    private String downloadLine(Downloader.Task t) {
        if (t.total > 0) {
            long left = Math.max(0, t.total - t.done);
            return Downloader.sizeText(t.done) + " / " + Downloader.sizeText(t.total)
                    + " · " + t.percent() + "% · " + Downloader.sizeText(t.speed)
                    + "/s · বাকি " + Downloader.sizeText(left);
        }
        return Downloader.sizeText(t.done) + " · " + Downloader.sizeText(t.speed) + "/s";
    }

    private void startDownloadTicker(final LinearLayout host) {
        dlHost = host;
        dlHandler.removeCallbacksAndMessages(null);
        dlHandler.postDelayed(new Runnable() {
            public void run() {
                if (overlay == null || dlHost != host) return;
                java.util.List<Downloader.Task> active = Downloader.active();
                boolean same = active.size() == activeRows.size();
                if (same) {
                    for (int i = 0; i < active.size(); i++) {
                        if (activeRows.get(i).task != active.get(i)) {
                            same = false;
                            break;
                        }
                    }
                }
                if (!same) {
                    renderDownloads(host);
                } else {
                    for (ActiveRow row : activeRows) {
                        row.bar.setProgress((int) (row.task.percent() * 10));
                        row.detail.setText(downloadLine(row.task));
                    }
                }
                if (!Downloader.active().isEmpty()) {
                    dlHandler.postDelayed(this, 500);
                }
            }
        }, 400);
    }

    private View recordRow(final String name, final String uri, final String mime, long size) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        ripple(row);

        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(rounded(cSurface, 11));
        TextView ico = new TextView(this);
        ico.setText(iconFor(mime, name));
        ico.setTextSize(18);
        ico.setGravity(Gravity.CENTER);
        tile.addView(ico, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(tile, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, 0, 0);

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(14);
        nameView.setTextColor(cText);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        texts.addView(nameView);

        TextView sub = new TextView(this);
        sub.setText("সম্পন্ন" + (size > 0 ? " · " + Downloader.sizeText(size) : ""));
        sub.setTextSize(11);
        sub.setTextColor(cMuted);
        texts.addView(sub);

        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (uri.length() == 0) {
                    toast("ফাইলটা পাওয়া যায়নি");
                    return;
                }
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(Uri.parse(uri), mime.length() == 0 ? "*/*" : mime);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(i);
                } catch (Exception e) {
                    toast("ফাইল খোলার মতো অ্যাপ নেই");
                }
            }
        });
        return row;
    }

    // ---------------------------------------------------------------- history

    private String shortTitle(String title, String url) {
        String t = title == null ? "" : title.replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " ").trim();
        if (t.length() == 0 || t.startsWith("http://") || t.startsWith("https://")
                || t.equals("about:blank")) {
            String h = Favicon.hostOf(url);
            return h.length() == 0 ? prettyUrl(url) : h;
        }
        if (t.length() > 70) t = t.substring(0, 70).trim() + "…";
        return t;
    }

    private View entryRow(String title, final String url,
                          final View.OnClickListener onTap,
                          final View.OnLongClickListener onLong) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(9), dp(14), dp(9));
        ripple(row);

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Favicon.bind(this, url, icon, cAccent, Color.WHITE);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(30), dp(30));
        ilp.rightMargin = dp(14);
        row.addView(icon, ilp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText(shortTitle(title, url));
        t.setTextSize(13.5f);
        t.setTextColor(cText);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(t);

        TextView u = new TextView(this);
        u.setText(prettyUrl(url));
        u.setTextSize(11);
        u.setTextColor(cMuted);
        u.setSingleLine(true);
        u.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        u.setPadding(0, dp(2), 0, 0);
        texts.addView(u);

        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (onTap != null) row.setOnClickListener(onTap);
        if (onLong != null) row.setOnLongClickListener(onLong);
        return row;
    }

    private LinearLayout fullPanel(String titleText, View.OnClickListener extra) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(cBg);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(18), dp(16), dp(10), dp(8));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(cText);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (extra != null) {
            TextView more = glyphButton("⋮", 18, cMuted);
            more.setPadding(dp(12), dp(6), dp(12), dp(6));
            more.setOnClickListener(extra);
            head.addView(more);
        }

        TextView close = glyphButton("✕", 16, cMuted);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeOverlay(); }
        });
        head.addView(close);
        panel.addView(head);
        return panel;
    }

    private void showHistory() {
        final List<String[]> items = loadList("history");

        LinearLayout panel = fullPanel("ইতিহাস (" + items.size() + ")", new View.OnClickListener() {
            public void onClick(View v) {
                dialog().setTitle("ইতিহাস মুছবেন?")
                        .setPositiveButton("মুছুন", (d, w) -> {
                            saveList("history", new ArrayList<>());
                            closeOverlay();
                            showHistory();
                        })
                        .setNegativeButton("না", null).show();
            }
        });

        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, 0, 0, dp(20));

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("ইতিহাস খালি।\nকোনো সাইটে গেলে সেটা এখানে জমা হবে।");
            empty.setTextSize(13);
            empty.setTextColor(cMuted);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(60), dp(16), dp(20));
            rows.addView(empty);
        }

        for (int i = 0; i < items.size(); i++) {
            final String title = items.get(i)[0];
            final String url = items.get(i)[1];
            rows.addView(entryRow(title, url, new View.OnClickListener() {
                public void onClick(View v) {
                    closeOverlay();
                    if (current != null) current.loadUrl(url);
                }
            }, new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    dialog().setTitle(shortTitle(title, url))
                            .setItems(new String[]{"নতুন ট্যাবে খুলুন", "লিংক কপি", "ইতিহাস থেকে মুছুন"},
                                    (d, which) -> {
                                        if (which == 0) {
                                            newTab(url, true);
                                        } else if (which == 1) {
                                            android.content.ClipboardManager cb =
                                                    (android.content.ClipboardManager)
                                                            getSystemService(CLIPBOARD_SERVICE);
                                            cb.setPrimaryClip(android.content.ClipData
                                                    .newPlainText("লিংক", url));
                                            toast("লিংক কপি হয়েছে");
                                        } else {
                                            removeHistory(url);
                                            closeOverlay();
                                            showHistory();
                                        }
                                    })
                            .show();
                    return true;
                }
            }));
        }

        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(cBg);
        box.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        openOverlay(box);
    }

    private void removeHistory(String url) {
        List<String[]> h = loadList("history");
        for (int i = h.size() - 1; i >= 0; i--) {
            if (h.get(i)[1].equals(url)) h.remove(i);
        }
        saveList("history", h);
    }

    // ---------------------------------------------------------------- favourite sites

    private List<String[]> defaultSites() {
        List<String[]> s = new ArrayList<>();
        s.add(new String[]{"প্রথম আলো", "https://www.prothomalo.com"});
        s.add(new String[]{"কালের কণ্ঠ", "https://www.kalerkantho.com"});
        s.add(new String[]{"বিডিনিউজ২৪", "https://bangla.bdnews24.com"});
        s.add(new String[]{"উইকিপিডিয়া", "https://bn.wikipedia.org"});
        s.add(new String[]{"ইউটিউব", "https://m.youtube.com"});
        s.add(new String[]{"ফেসবুক", "https://m.facebook.com"});
        s.add(new String[]{"জিমেইল", "https://mail.google.com"});
        s.add(new String[]{"বাংলানিউজ", "https://www.banglanews24.com"});
        return s;
    }

    private List<String[]> loadSites() {
        if (prefs.getString("sites", "").length() == 0) {
            List<String[]> d = defaultSites();
            saveList("sites", d);
            return d;
        }
        List<String[]> s = loadList("sites");
        if (s.isEmpty()) {
            s = defaultSites();
            saveList("sites", s);
        }
        return s;
    }

    private void injectSites(WebView view) {
        if (view == null) return;
        JSONArray arr = new JSONArray();
        for (String[] s : loadSites()) {
            try {
                JSONObject o = new JSONObject();
                o.put("n", s[0]);
                o.put("u", s[1]);
                arr.put(o);
            } catch (Exception ignored) { }
        }
        view.evaluateJavascript("window.setSites && window.setSites(" + arr.toString() + ");", null);
    }

    private void showSiteEditor() {
        final List<String[]> sites = loadSites();

        LinearLayout panel = fullPanel("প্রিয় সাইট", new View.OnClickListener() {
            public void onClick(View v) {
                dialog().setTitle("ডিফল্ট তালিকায় ফিরবেন?")
                        .setPositiveButton("ফিরিয়ে আনো", (d, w) -> {
                            saveList("sites", defaultSites());
                            closeOverlay();
                            showSiteEditor();
                            if (current != null) injectSites(current);
                        })
                        .setNegativeButton("না", null).show();
            }
        });

        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < sites.size(); i++) {
            final int idx = i;
            final String name = sites.get(i)[0];
            final String url = sites.get(i)[1];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(7), dp(8), dp(7));

            ImageView icon = new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Favicon.bind(this, url, icon, cAccent, Color.WHITE);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(30), dp(30));
            ilp.rightMargin = dp(12);
            row.addView(icon, ilp);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);

            TextView t = new TextView(this);
            t.setText(name);
            t.setTextSize(14);
            t.setTextColor(cText);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(t);

            TextView u = new TextView(this);
            u.setText(prettyUrl(url));
            u.setTextSize(11);
            u.setTextColor(cMuted);
            u.setSingleLine(true);
            u.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            texts.addView(u);

            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.addView(smallButton(i == 0 ? "" : "▲", new Runnable() {
                public void run() { moveSite(idx, -1); }
            }));
            row.addView(smallButton(i == sites.size() - 1 ? "" : "▼", new Runnable() {
                public void run() { moveSite(idx, 1); }
            }));
            row.addView(smallButton("✕", new Runnable() {
                public void run() {
                    List<String[]> now = loadSites();
                    if (idx < now.size()) now.remove(idx);
                    saveList("sites", now);
                    closeOverlay();
                    showSiteEditor();
                    if (current != null) injectSites(current);
                }
            }));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    closeOverlay();
                    if (current != null) current.loadUrl(url);
                }
            });
            rows.addView(row);
        }

        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout add = new LinearLayout(this);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(16), dp(10), dp(16), dp(22));
        add.setBackground(rounded(cSurface, 16));
        TextView plus = new TextView(this);
        plus.setText("+ নতুন সাইট যোগ করুন");
        plus.setTextSize(14);
        plus.setTypeface(Typeface.DEFAULT_BOLD);
        plus.setTextColor(cAccent);
        plus.setGravity(Gravity.CENTER);
        plus.setPadding(0, dp(12), 0, dp(12));
        add.addView(plus);
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { addSiteDialog(); }
        });
        panel.addView(add);

        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(cBg);
        box.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        openOverlay(box);
    }

    private TextView smallButton(String glyph, final Runnable action) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextSize(13);
        t.setTextColor(glyph.length() == 0 ? cBg : cMuted);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        ripple(t);
        if (glyph.length() > 0) {
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { action.run(); }
            });
        }
        return t;
    }

    private void moveSite(int index, int dir) {
        List<String[]> sites = loadSites();
        int target = index + dir;
        if (index < 0 || index >= sites.size() || target < 0 || target >= sites.size()) return;
        String[] a = sites.get(index);
        sites.set(index, sites.get(target));
        sites.set(target, a);
        saveList("sites", sites);
        closeOverlay();
        showSiteEditor();
        if (current != null) injectSites(current);
    }

    private void addSiteDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(10), dp(22), 0);

        final EditText name = new EditText(this);
        name.setHint("নাম (যেমন প্রথম আলো)");
        name.setSingleLine(true);
        box.addView(name);

        final EditText url = new EditText(this);
        url.setHint("ঠিকানা (prothomalo.com)");
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(url);

        dialog().setTitle("নতুন সাইট")
                .setView(box)
                .setPositiveButton("যোগ করুন", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String u = url.getText().toString().trim();
                    if (u.length() == 0) {
                        toast("সাইটের ঠিকানা লিখো");
                        return;
                    }
                    if (!u.startsWith("http://") && !u.startsWith("https://")) {
                        u = "https://" + u;
                    }
                    if (n.length() == 0) n = Favicon.hostOf(u);
                    List<String[]> sites = loadSites();
                    sites.add(new String[]{n, u});
                    saveList("sites", sites);
                    closeOverlay();
                    showSiteEditor();
                    if (current != null) injectSites(current);
                })
                .setNegativeButton("বাতিল", null)
                .show();
    }

    private String iconFor(String mime, String name) {
        String m = mime == null ? "" : mime.toLowerCase(java.util.Locale.US);
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.US);
        if (m.startsWith("image/") || n.endsWith(".jpg") || n.endsWith(".png")
                || n.endsWith(".webp")) return "🖼️";
        if (m.startsWith("video/") || n.endsWith(".mp4") || n.endsWith(".mkv")) return "🎬";
        if (m.startsWith("audio/") || n.endsWith(".mp3") || n.endsWith(".m4a")) return "🎵";
        if (m.contains("pdf") || n.endsWith(".pdf")) return "📕";
        if (m.contains("zip") || m.contains("compressed") || n.endsWith(".zip")
                || n.endsWith(".apk")) return "🗜️";
        return "📄";
    }

    // ---------------------------------------------------------------- default browser

    private boolean isDefaultBrowser() {
        return BrowserRole.isDefault(this);
    }

    private void askDefaultBrowser() {
        if (isDefaultBrowser()) {
            toast("বাংলা ব্রাউজার এখনই ডিফল্ট ব্রাউজার");
            return;
        }
        if (!BrowserRole.request(this, REQ_ROLE)) {
            toast("এই ফোনে সেটিংস খোলা যায়নি");
        }
    }

    // ---------------------------------------------------------------- page tools

    private void copyLink() {
        if (current == null) return;
        String u = current.getUrl();
        if (!isRealPage(u)) {
            toast("এই পেজের লিংক নেই");
            return;
        }
        try {
            android.content.ClipboardManager cb =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(android.content.ClipData.newPlainText("লিংক", u));
            toast("লিংক কপি হয়েছে");
        } catch (Exception e) {
            toast("কপি করা যায়নি");
        }
    }

    private void findInPage() {
        if (current == null) return;
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setHint("পেজে লেখা খুঁজুন");
        input.setTextColor(cText);
        input.setHintTextColor(cMuted);
        input.setPadding(dp(20), dp(10), dp(20), dp(10));

        dialog().setTitle("পেজে খুঁজুন")
                .setView(input)
                .setPositiveButton("খুঁজুন", (d, w) -> {
                    String q = input.getText().toString();
                    if (q.length() > 0) current.findAllAsync(q);
                })
                .setNegativeButton("বন্ধ", (d, w) -> {
                    if (current != null) current.clearMatches();
                })
                .show();
    }

    private void printPage() {
        if (current == null) return;
        try {
            android.print.PrintManager pm =
                    (android.print.PrintManager) getSystemService(PRINT_SERVICE);
            String name = pageTitle.getText().toString();
            if (name.length() == 0) name = "বাংলা ব্রাউজার";
            pm.print(name, current.createPrintDocumentAdapter(name), null);
        } catch (Exception e) {
            toast("এই ফোনে প্রিন্ট চালু নেই");
        }
    }

    private void closeAllTabs() {
        for (Tab t : new ArrayList<>(tabs)) {
            try {
                holder.removeView(t.web);
                t.web.stopLoading();
                t.web.destroy();
            } catch (Exception ignored) { }
        }
        tabs.clear();
        activeIndex = 0;
        current = null;
        newTab(homeUrl(), true);
        toast("সব ট্যাব বন্ধ হয়েছে");
    }

    // ---------------------------------------------------------------- fullscreen video

    private void exitFullscreen() {
        if (fullView == null) return;
        try {
            screen.removeView(fullView);
        } catch (Exception ignored) { }
        fullView = null;
        hideSystemBars(false);
        WebChromeClient.CustomViewCallback cb = fullCallback;
        fullCallback = null;
        if (cb != null) cb.onCustomViewHidden();
    }

    private void hideSystemBars(boolean hide) {
        try {
            Window w = getWindow();
            View d = w.getDecorView();
            if (hide) {
                w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            } else {
                w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                d.setSystemUiVisibility(darkMode ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Exception ignored) { }
    }


    // ---------------------------------------------------------------- overlays

    private void openOverlay(View v) {
        closeOverlay();
        overlay = v;
        screen.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void closeOverlay() {
        dlHandler.removeCallbacksAndMessages(null);
        dlHost = null;
        if (overlay != null) {
            screen.removeView(overlay);
            overlay = null;
        }
    }

    // ---------------------------------------------------------------- webview

    private WebView buildWebView() {
        WebView web = new WebView(this);
        web.setBackgroundColor(cBg);
        web.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(prefs.getBoolean("js", true));
        web.addJavascriptInterface(new TranslateBridge(), "BBTranslate");
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(prefs.getBoolean("images", true));
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setTextZoom(prefs.getInt("textzoom", 100));
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
                return handleUrl(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                                                   android.webkit.WebResourceRequest request) {
                return handleUrl(view, request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap fav) {
                Tab t = tabOf(view);
                if (t != null) t.url = url;
                progress.setVisibility(View.VISIBLE);
                if (url != null && url.startsWith(HOME_URL)) {
                    urlBar.setText("");
                    pageTitle.setText("");
                } else if (view == current && isRealPage(url)) {
                    urlBar.setText(prettyUrl(url));
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Tab t = tabOf(view);
                if (t != null) {
                    t.url = url;
                    if (t.title.length() == 0) t.title = prettyUrl(url);
                }
                progress.setVisibility(View.GONE);
                if (view == current) updateNavButtons();
                if (isRealPage(url)) addHistory(t == null ? null : t.title, url);
                if (url != null && url.startsWith(HOME_URL)) {
                    injectSites(view);
                    injectRecent(view);
                }
                if (isRealPage(url)) {
                    if (autoTranslate) injectTranslate(view);
                    if (darkMode) injectDark(view);
                }
            }

            @Override
            public void onReceivedError(WebView view, int code, String desc, String failingUrl) {
                if (Build.VERSION.SDK_INT >= 23) return;
                if (!isRealPage(failingUrl) || view != current) return;
                progress.setVisibility(View.GONE);
                showErrorPage(view, failingUrl);
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request == null || !request.isForMainFrame()) return;
                String failingUrl = request.getUrl() == null ? null : request.getUrl().toString();
                if (!isRealPage(failingUrl) || view != current) return;
                progress.setVisibility(View.GONE);
                showErrorPage(view, failingUrl);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (view != current) return;
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                Tab t = tabOf(view);
                if (t != null) t.title = title == null ? "" : title;
                if (view == current) pageTitle.setText(t == null ? "" : t.title);
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

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullView = view;
                fullCallback = callback;
                screen.addView(fullView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                closeOverlay();
                hideSystemBars(true);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });

        web.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                Downloader.start(MainActivity.this, url, ua, cd, mime, len);
            }
        });

        return web;
    }

    private void applySettings() {
        String engine = prefs.getString("engine", "google");
        int zoom = prefs.getInt("textzoom", 100);
        boolean js = prefs.getBoolean("js", true);
        boolean images = prefs.getBoolean("images", true);
        for (Tab t : tabs) {
            WebSettings s = t.web.getSettings();
            s.setJavaScriptEnabled(js);
            s.setLoadsImagesAutomatically(images);
            s.setTextZoom(zoom);
            s.setUserAgentString(desktopMode ? UA_DESKTOP : uaMobile);
        }
        this.engine = engine;
    }

    private String engine = "google";

    private String searchUrl(String q) {
        String e = engine;
        if ("ddg".equals(e)) return "https://duckduckgo.com/?q=" + enc(q);
        if ("bing".equals(e)) return "https://www.bing.com/search?q=" + enc(q);
        return "https://www.google.com/search?q=" + enc(q);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_ROLE) {
            toast(isDefaultBrowser() ? "ডিফল্ট ব্রাউজার সেট হয়েছে" : "ডিফল্ট ব্রাউজার সেট হয়নি");
            return;
        }
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
        if (url == null) return "";
        String u = url;
        if (u.startsWith("https://")) u = u.substring(8);
        else if (u.startsWith("http://")) u = u.substring(7);
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private String homeUrl() {
        return darkMode ? HOME_URL + "#dark" : HOME_URL;
    }

    private boolean handleUrl(WebView view, String url) {
        if (url == null) return true;
        if (url.startsWith("bangla://")) {
            internal(view, url);
            return true;
        }
        if ((url.startsWith("http://") || url.startsWith("https://"))
                && prefs.getBoolean("newtab", false)) {
            newTab(url, true);
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

    private void internal(WebView view, String url) {
        Uri u = Uri.parse(url);
        String host = u.getHost() == null ? "" : u.getHost();
        if (host.equals("search")) {
            String q = u.getQueryParameter("q");
            if (q != null && q.trim().length() > 0) {
                String target = searchUrl(q.trim());
                view.loadUrl(target);
                addHistory(q.trim(), target);
            }
        } else if (host.equals("reload")) {
            view.reload();
        } else if (host.equals("open")) {
            String s = u.getQueryParameter("u");
            if (s != null) {
                String target = s;
                if (!target.startsWith("http")) target = "https://" + target;
                view.loadUrl(target);
            }
        }
    }

    private void go(String input) {
        String t = input == null ? "" : input.trim();
        if (t.length() == 0 || current == null) return;
        hideKeyboard();
        if (t.startsWith("bangla://")) {
            internal(current, t);
            return;
        }
        if (t.contains(" ") || !t.contains(".")) {
            String search = searchUrl(t);
            current.loadUrl(search);
            addHistory(t, search);
            return;
        }
        if (!t.startsWith("http://") && !t.startsWith("https://")) t = "https://" + t;
        current.loadUrl(t);
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

    private void showErrorPage(WebView view, String url) {
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
        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
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
        h.add(0, new String[]{shortTitle(title, url), url});
        while (h.size() > 200) h.remove(h.size() - 1);
        saveList("history", h);
    }

    private void toggleBookmark() {
        if (current == null) return;
        String url = current.getUrl();
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
        if (t.length() == 0) t = prettyUrl(url);
        b.add(0, new String[]{t, url});
        saveList("bookmarks", b);
        updateNavButtons();
        toast("বুকমার্কে রাখা হয়েছে");
    }

    private void injectRecent(WebView view) {
        if (view == null) return;
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
        view.evaluateJavascript("window.setRecent && window.setRecent(" + arr.toString() + ");", null);
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
                .setItems(names, (d, which) -> {
                    if (current != null) current.loadUrl(items.get(which)[1]);
                });
        if (clearable) {
            b.setNegativeButton("সব মুছুন", (d, w) -> {
                saveList(key, new ArrayList<>());
                toast("মুছে ফেলা হয়েছে");
            });
        }
        b.setPositiveButton("বন্ধ", null).show();
    }

    private String asset(String name) {
        String cached = assetCache.get(name);
        if (cached != null) return cached;
        try {
            java.io.InputStream in = getAssets().open(name);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            String js = new String(out.toByteArray(), "UTF-8");
            assetCache.put(name, js);
            return js;
        } catch (Exception e) {
            return null;
        }
    }

    private void injectJs(WebView view, String name) {
        if (!prefs.getBoolean("js", true)) return;
        String js = asset(name);
        if (js != null) view.evaluateJavascript(js, null);
    }

    private void injectTranslate(WebView view) {
        injectJs(view, "translate.js");
    }

    /** ইউটিউবের নিজের ডার্ক/লাইট থিম ঠিক হয় তার PREF কুকি থেকে।
        রাতের মোডে কুকিতে ডার্ক বসিয়ে দিই, তাই ইউটিউব নিজের আসল কালো থিমে আসে।
        জোর করে কালোতে বরং লাইট করে দিই, কারণ পুরো পেজ আমরা উল্টে দিই। */
    private void syncYouTubeTheme() {
        try {
            String f6 = null;
            if (darkMode && !forceDark) f6 = "400";   // ইউটিউব ডার্ক
            else if (forceDark) f6 = "8";             // ইউটিউব লাইট, তারপর আমরা উল্টাব
            if (f6 == null) return;

            android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
            cm.setAcceptCookie(true);
            String cur = cm.getCookie("https://www.youtube.com");
            String val = "f6=" + f6;
            if (cur != null && cur.contains("PREF=")) {
                String pref = null;
                for (String p : cur.split(";\\s*")) {
                    if (p.startsWith("PREF=")) pref = p.substring(5);
                }
                if (pref != null && pref.length() > 0) {
                    StringBuilder sb = new StringBuilder();
                    boolean has = false;
                    for (String kv : pref.split("&")) {
                        if (kv.length() == 0) continue;
                        if (kv.startsWith("f6=")) { kv = "f6=" + f6; has = true; }
                        if (sb.length() > 0) sb.append('&');
                        sb.append(kv);
                    }
                    if (!has) { if (sb.length() > 0) sb.append('&'); sb.append("f6=").append(f6); }
                    val = sb.toString();
                }
            }
            String cookie = "PREF=" + val + "; Domain=.youtube.com; Path=/; Secure";
            cm.setCookie("https://www.youtube.com", cookie);
            cm.setCookie("https://m.youtube.com", cookie);
            cm.flush();
        } catch (Exception e) { /* কুকি না বসালেও বাকি কাজ চলবে */ }
    }

    private void injectDark(WebView view) {
        if (!prefs.getBoolean("js", true)) return;
        String js = asset("dark.js");
        if (js == null) return;
        // জোর করে কালো: সাইটের নিজের ডার্ক থিম বাদ দিয়ে সব পেজ উল্টে দেওয়া হবে
        view.evaluateJavascript(
                "window.__bbForceDark=" + (forceDark ? "1" : "0") + ";" + js, null);
    }

    /** সাইটের CSP-তে আটকে গেলে অনুবাদ এই ব্রিজ দিয়ে আনা হয়। */
    private static class TranslateBridge {
        @JavascriptInterface
        public String get(String url) {
            if (url == null || !url.startsWith("https://translate.googleapis.com/")) return "";
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36");
                if (c.getResponseCode() != 200) return "";
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                return new String(out.toByteArray(), "UTF-8");
            } catch (Exception e) {
                return "";
            } finally {
                if (c != null) c.disconnect();
            }
        }
    }

    private void about() {
        String msg = "বাংলা ব্রাউজার\nসংস্করণ ১.২.১.১২\n\n"
                + "সহজ, দ্রুত ও সম্পূর্ণ বাংলা ব্রাউজার।\n"
                + "ট্যাব, বুকমার্ক, ইতিহাস, রাতের মোড — সব একসাথে।\n"
                + "ইংরেজি পেজ নিজে থেকেই বাংলায় অনুবাদ হয়ে যায়।\n\n"
                + "নির্মাতা\n"
                + "মামুন (@Mamun015)\n"
                + "github.com/Mamun015\n\n"
                + "সোর্স কোড: ওপেন সোর্স\n"
                + "লাইসেন্স: Mozilla Public License 2.0 (MPL-2.0)\n"
                + "github.com/Mamun015/bangla-browser\n\n"
                + "Made in Bangladesh";
        dialog().setTitle("অ্যাপ সম্পর্কে")
                .setMessage(msg)
                .setPositiveButton("ঠিক আছে", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
