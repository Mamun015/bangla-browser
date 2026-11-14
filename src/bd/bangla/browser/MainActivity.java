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
    private static final String VERSION = "2.0";
    private boolean autoTranslate = true;
    private final java.util.HashMap<String, String> assetCache = new java.util.HashMap<String, String>();
    private static final String UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final int MAX_TABS = 50;

    private WebView current;
    private EditText urlBar;
    private ProgressBar progress;
    private View btnBack, btnForward;
    private ImageView reloadIcon;
    private boolean pageLoading;
    private TextView pageTitle, tabBadge, navTabLabel;
    private LinearLayout rootView, bottomBar, navBar, suggestBox;
    private final android.os.Handler suggestHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable suggestPending;
    private int suggestSeq;
    private String suggestQuery = "";
    private List<String> suggestRemote = new ArrayList<String>();
    private String suggestRemoteQuery = "";
    private FrameLayout screen, holder;
    private SharedPreferences prefs;
    private View overlay;

    private final List<Tab> tabs = new ArrayList<>();
    private final java.util.HashSet<String> httpTried = new java.util.HashSet<>();
    private int activeIndex = 0;

    private boolean darkMode, desktopMode, forceDark, langEn;
    private String uaMobile;
    private int cBg, cSurface, cSurface2, cText, cMuted, cAccent, cAccentSoft, cLine;
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
        String lastGood = "";
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
        if (T_FORCE.equals(t)) return Lang.t("জোর করে কালো");
        if (T_DARK.equals(t)) return Lang.t("রাতের মোড");
        return Lang.t("সিস্টেম সেটিং");
    }

    private void pickTheme() {
        final String[] names = {Lang.t("সিস্টেম সেটিং অনুযায়ী"), Lang.t("রাতের মোড"), Lang.t("জোর করে কালো")};
        final String[] keys = {T_SYSTEM, T_DARK, T_FORCE};
        int checked = 0;
        String cur = themeKey();
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(cur)) checked = i;
        dialog().setTitle(Lang.t("থিম"))
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
        Lang.init(prefs);
        langEn = Lang.isEn();
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

        // অন্য অ্যাপ থেকে http/https লিংক চেপে ব্রাউজার খুললে সেই লিংকটাই খুলবে
        Intent launch = getIntent();
        if (launch != null && launch.getData() != null) {
            String link = launch.getData().toString();
            if (link.startsWith("http://") || link.startsWith("https://")) {
                newTab(link, true);
                setIntent(new Intent()); // recreate() হলে যেন আবার না খোলে
            }
        }
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
        if (!prefs.getBoolean("restore", true)) {
            newTab(homeUrl(), true);
            return;
        }
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
        Lang.init(prefs);
        if (Lang.isEn() != langEn) {
            recreate();
            return;
        }
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
            toast(Lang.t("ট্যাব বন্ধ হয়েছে"));
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 2000) {
            super.onBackPressed();
        } else {
            lastBackPress = now;
            toast(Lang.t("বের হতে আবার চাপুন"));
        }
    }

    // ---------------------------------------------------------------- colors

    private void pickColors() {
        Theme t = new Theme(darkMode);
        cBg = t.bg;
        cSurface = t.surface;
        cSurface2 = t.surface2;
        cText = t.text;
        cMuted = t.muted;
        cAccent = t.accent;
        cAccentSoft = t.accentSoft;
        cLine = t.line;
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

    /** গোল কোণে রিপল — আধুনিক বাটন/সারির জন্য */
    private void rippleRound(View v, float radius) {
        if (Build.VERSION.SDK_INT < 21) {
            ripple(v);
            return;
        }
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(radius));
        android.content.res.ColorStateList tint = ColorStateList.valueOf(
                darkMode ? 0x2E34D399 : 0x1A00795A);
        v.setBackground(new android.graphics.drawable.RippleDrawable(tint, null, mask));
    }

    /** আগে থেকে থাকা পটভূমির ওপরে রিপল */
    private void rippleOn(View v, float radius, android.graphics.drawable.Drawable base) {
        if (Build.VERSION.SDK_INT < 21) return;
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(radius));
        android.content.res.ColorStateList tint = ColorStateList.valueOf(
                darkMode ? 0x2E34D399 : 0x1A00795A);
        v.setBackground(new android.graphics.drawable.RippleDrawable(tint, base, mask));
    }

    /** গোল কোণা ভরাট পটভূমি — বোতাম, চিপ, আইকন টাইলের জন্য */
    private GradientDrawable pill(int color, float radius) {
        return rounded(color, radius);
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

    /** res/drawable-এর ভেক্টর আইকন, রঙ বদলানো যায় */
    private ImageView icon(String name, int sizeDp, int color) {
        ImageView v = new ImageView(this);
        int id = getResources().getIdentifier("ic_" + name, "drawable", getPackageName());
        if (id != 0) {
            v.setImageResource(id);
            v.setColorFilter(color);
        }
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
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
        brand.setText(Lang.t("বাংলা ব্রাউজার"));
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
        addrPill.setBackground(roundedBorder(cSurface, 26));
        if (Build.VERSION.SDK_INT >= 21) addrPill.setElevation(dp(1));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView lock = new TextView(this);
        lock.setText("🔒");
        lock.setTextSize(12);
        lock.setPadding(dp(15), 0, dp(7), 0);
        addrPill.addView(lock);

        urlBar = new EditText(this);
        urlBar.setSingleLine(true);
        urlBar.setTextSize(14);
        urlBar.setTextColor(cText);
        urlBar.setHintTextColor(cMuted);
        urlBar.setHint(Lang.t("সার্চ করুন বা ঠিকানা লিখুন"));
        urlBar.setBackground(null);
        urlBar.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        urlBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBar.setPadding(0, dp(9), 0, dp(9));
        urlBar.setSelectAllOnFocus(true);
        urlBar.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (urlBar.hasFocus()) showSuggestions(s == null ? "" : s.toString());
            }
            public void afterTextChanged(android.text.Editable s) { }
        });
        urlBar.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                if (!has) hideSuggestions();
            }
        });
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

        final ImageView reload = icon("refresh", 20, cMuted);
        reloadIcon = reload;
        LinearLayout reloadBox = new LinearLayout(this);
        reloadBox.setGravity(Gravity.CENTER);
        reloadBox.setPadding(dp(10), dp(7), dp(12), dp(7));
        rippleRound(reloadBox, 20);
        reloadBox.addView(reload, new LinearLayout.LayoutParams(dp(20), dp(20)));
        reloadBox.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (pageLoading) {
                    if (current != null) current.stopLoading();
                    setReloadState(false);
                    return;
                }
                reload.animate().rotationBy(360f).setDuration(480)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                if (current != null) current.reload();
            }
        });
        addrPill.addView(reloadBox);

        topBar.addView(addrPill, pl);
        rootView.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        suggestBox = new LinearLayout(this);
        suggestBox.setOrientation(LinearLayout.VERTICAL);
        suggestBox.setBackground(roundedBorder(cSurface, 16));
        suggestBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams suggestLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        suggestLp.leftMargin = dp(12);
        suggestLp.rightMargin = dp(12);
        suggestLp.topMargin = dp(2);
        suggestLp.bottomMargin = dp(6);
        rootView.addView(suggestBox, suggestLp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        if (Build.VERSION.SDK_INT >= 21) progress.setProgressTintList(ColorStateList.valueOf(cAccent));
        rootView.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        holder = new FrameLayout(this);
        rootView.addView(holder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View barLine = new View(this);
        barLine.setBackgroundColor(cLine);
        rootView.addView(barLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(cBg);
        bottomBar.setPadding(dp(4), dp(2), dp(4), dp(3));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        navBar.setPadding(0, 0, 0, 0);
        bottomBar.addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        btnBack = navItem("back", Lang.t("পিছনে"), new Runnable() {
            public void run() { if (current != null && current.canGoBack()) current.goBack(); }
        });
        btnForward = navItem("forward", Lang.t("সামনে"), new Runnable() {
            public void run() { if (current != null && current.canGoForward()) current.goForward(); }
        });
        navItem("home", Lang.t("হোম"), new Runnable() {
            public void run() {
                if (current == null) return;
                urlBar.setText("");
                pageTitle.setText("");
                current.loadUrl(homeUrl());
            }
        });
        LinearLayout tabBox = navItemBox("tab", Lang.t("ট্যাব"), new Runnable() {
            public void run() { showTabSwitcher(); }
        });
        navTabLabel = (TextView) tabBox.getChildAt(1);
        navMenuButton();

        rootView.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        screen.addView(rootView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(screen);
        updateNavButtons();
    }

    private View navItem(String iconName, String label, final Runnable action) {
        return navItemBox(iconName, label, action).getChildAt(0);
    }

    private LinearLayout navItemBox(String iconName, String label, final Runnable action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, dp(3), 0, dp(1));
        rippleRound(box, 14);
        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });

        ImageView g = icon(iconName, 25, cText);
        box.addView(g);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(8.5f);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(cMuted);
        t.setPadding(0, dp(1), 0, 0);
        t.setIncludeFontPadding(false);
        box.addView(t);

        navBar.addView(box, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return box;
    }

    /** নিচের বারের ডানদিকের গোল মেনু বোতাম */
    private void navMenuButton() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, dp(2), 0, dp(1));
        rippleRound(box, 14);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(cAccent);
        LinearLayout menuCircle = new LinearLayout(this);
        menuCircle.setGravity(Gravity.CENTER);
        menuCircle.setBackground(circle);
        ImageView g = icon("menu", 20, darkMode ? Color.parseColor("#04241B") : Color.WHITE);
        g.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
        menuCircle.addView(g);
        box.addView(menuCircle, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView t = new TextView(this);
        t.setText(Lang.t("মেনু"));
        t.setTextSize(8.5f);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(cMuted);
        t.setPadding(0, dp(1), 0, 0);
        t.setIncludeFontPadding(false);
        box.addView(t);

        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showMenu(); }
        });
        navBar.addView(box, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void updateNavButtons() {
        if (current == null) return;
        btnBack.setAlpha(current.canGoBack() ? 1f : 0.32f);
        btnForward.setAlpha(current.canGoForward() ? 1f : 0.32f);
        updateTabBadge();
    }

    private void updateTabBadge() {
        tabBadge.setText(String.valueOf(tabs.size()));
        if (navTabLabel != null) {
            navTabLabel.setText(tabs.size() > 1 ? Lang.t("ট্যাব ") + tabs.size() : Lang.t("ট্যাব"));
            navTabLabel.setTextColor(tabs.size() > 1 ? cAccent : cMuted);
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
            toast(Lang.t("সর্বোচ্চ ") + MAX_TABS + Lang.t(" ট্যাব খোলা যায়"));
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
            if (tabs.get(i).web != null) {
                tabs.get(i).web.setVisibility(i == index ? View.VISIBLE : View.GONE);
            }
        }
        Tab t = tabs.get(index);
        if (t.web == null) {
            t.web = buildWebView();
            holder.addView(t.web, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            t.web.setVisibility(View.VISIBLE);
            if (t.url != null && t.url.length() > 0) t.web.loadUrl(t.url);
        }
        current = t.web;
        if (t.web.getUrl() == null && t.url != null && t.url.length() > 0) {
            t.web.loadUrl(t.url);
        }
        if (t.url != null && t.url.startsWith("file:")) {
            urlBar.setText("");
            urlBar.setHint(Lang.t("সার্চ করুন বা ঠিকানা লিখুন"));
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
        if (t.web != null) {
            holder.removeView(t.web);
            t.web.stopLoading();
            t.web.destroy();
        }
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
        box.setBackgroundColor(darkMode ? 0xF7080B0A : 0xF7FFFFFF);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(18), dp(16), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(Lang.t("ট্যাব (") + tabs.size() + ")");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(cText);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView done = new TextView(this);
        done.setText(Lang.t("সম্পন্ন"));
        done.setTextSize(13);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        done.setTextColor(cAccent);
        done.setPadding(dp(12), dp(8), dp(10), dp(8));
        rippleRound(done, 18);
        done.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeOverlay(); }
        });
        header.addView(done);

        panel.addView(header);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(12);
        alp.bottomMargin = dp(4);

        TextView add = new TextView(this);
        add.setText(Lang.t("＋ নতুন ট্যাব"));
        add.setTextSize(13);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setTextColor(cAccent);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(12), dp(11), dp(12), dp(11));
        add.setBackground(pill(cAccentSoft, 16));
        rippleOn(add, 16, pill(cAccentSoft, 16));
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeOverlay();
                newTab(homeUrl(), true);
            }
        });
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        addLp.rightMargin = dp(8);
        actions.addView(add, addLp);

        TextView closeAll = new TextView(this);
        closeAll.setText(Lang.t("সব ট্যাব বন্ধ"));
        closeAll.setTextSize(13);
        closeAll.setTypeface(Typeface.DEFAULT_BOLD);
        closeAll.setTextColor(cMuted);
        closeAll.setGravity(Gravity.CENTER);
        closeAll.setPadding(dp(12), dp(11), dp(12), dp(11));
        closeAll.setBackground(roundedBorder(cSurface, 16));
        rippleOn(closeAll, 16, roundedBorder(cSurface, 16));
        closeAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { confirmCloseAll(); }
        });
        actions.addView(closeAll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        panel.addView(actions, alp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(10), 0, 0);

        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            final Tab t = tabs.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(12), dp(6), dp(12));
            GradientDrawable cardBg = rounded(i == activeIndex ? cAccentSoft : cSurface, 16);
            cardBg.setStroke(dp(1), i == activeIndex ? cAccent : cLine);
            card.setBackground(cardBg);
            rippleOn(card, 16, cardBg);

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = dp(8);

            if (isRealPage(t.url)) {
                ImageView icon = new ImageView(this);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Favicon.bind(this, t.url, icon, cAccent, Color.WHITE);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(22), dp(22));
                ilp.rightMargin = dp(10);
                card.addView(icon, ilp);
            }

            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);

            TextView name = new TextView(this);
            String label = t.title;
            if (label == null || label.length() == 0) {
                label = t.url == null ? Lang.t("নতুন ট্যাব") : prettyUrl(t.url);
                if (label.startsWith("file:")) label = Lang.t("হোম");
            }
            name.setText(label);
            name.setTextSize(14);
            name.setTextColor(cText);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView(name);

            TextView addr = new TextView(this);
            if (t.url != null && t.url.startsWith(HOME_URL)) {
                addr.setText(Lang.t("হোম"));
            } else {
                addr.setText(t.url == null ? "" : t.url);
            }
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
            close.setTextSize(14);
            close.setTextColor(cMuted);
            close.setGravity(Gravity.CENTER);
            close.setPadding(dp(10), dp(8), dp(10), dp(8));
            rippleRound(close, 16);
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

    /** সব ট্যাব মুছে ফেলার আগে একবার জিজ্ঞাসা */
    private void confirmCloseAll() {
        dialog().setTitle(Lang.t("সব ট্যাব বন্ধ করবেন?"))
                .setMessage(Lang.t("সব ট্যাব মুছে নতুন করে শুরু হবে।"))
                .setPositiveButton(Lang.t("সব বন্ধ"), (d, w) -> {
                    closeOverlay();
                    closeAllTabs();
                })
                .setNegativeButton(Lang.t("না"), null)
                .show();
    }

    // ---------------------------------------------------------------- modern menu sheet

    private void showMenu() {
        final FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(0x8A000000);
        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeOverlay(); }
        });

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(darkMode ? cSurface : cBg);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, r, r, r, r});
        bg.setStroke(dp(1), cLine);
        sheet.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) sheet.setElevation(dp(20));
        sheet.setPadding(dp(10), dp(6), dp(10), dp(16));
        sheet.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { }
        });

        View grip = new View(this);
        grip.setBackground(rounded(cLine, 2));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(38), dp(4));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = dp(8);
        glp.bottomMargin = dp(6);
        sheet.addView(grip, glp);

        sheet.addView(sheetHeader());
        sheet.addView(quickActions(current != null && isBookmarked(current.getUrl())));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        boolean marked = current != null && isBookmarked(current.getUrl());
        list.addView(menuRow("star",
                marked ? Lang.t("বুকমার্ক সরান") : Lang.t("এই পেজ বুকমার্ক করুন"),
                null, null, new Runnable() {
                    public void run() { toggleBookmark(); }
                }));
        list.addView(menuRow("bookmarks", Lang.t("বুকমার্ক"),
                String.valueOf(loadList("bookmarks").size()), new Runnable() {
                    public void run() { showList(Lang.t("বুকমার্ক"), "bookmarks", true); }
                }));
        list.addView(menuRow("history", Lang.t("ইতিহাস"),
                String.valueOf(loadList("history").size()), new Runnable() {
                    public void run() { showHistory(); }
                }));
        list.addView(menuRow("apps", Lang.t("প্রিয় সাইট"),
                String.valueOf(loadSites().size()), new Runnable() {
                    public void run() { showSiteEditor(); }
                }));
        int running = Downloader.activeCount();
        list.addView(menuRow("download", Lang.t("ডাউনলোড"),
                running > 0 ? running + Lang.t(" চলছে")
                        : String.valueOf(downloadRecords().length()), new Runnable() {
                    public void run() { showDownloads(); }
                }));
        list.addView(menuRow("share", Lang.t("শেয়ার"), null, new Runnable() {
            public void run() {
                if (current == null) return;
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, current.getUrl());
                startActivity(Intent.createChooser(i, Lang.t("শেয়ার করুন")));
            }
        }));
        list.addView(menuRow("link", Lang.t("লিংক কপি"), null, new Runnable() {
            public void run() { copyLink(); }
        }));

        list.addView(sectionLabel(Lang.t("পেজ")));

        list.addView(menuRow("search", Lang.t("পেজে খুঁজুন"), null, new Runnable() {
            public void run() { findInPage(); }
        }));
        list.addView(menuRow("refresh", Lang.t("রিলোড"), null, new Runnable() {
            public void run() { if (current != null) current.reload(); }
        }));
        list.addView(menuRow("print", Lang.t("প্রিন্ট"), null, new Runnable() {
            public void run() { printPage(); }
        }));
        list.addView(toggleRow("desktop", Lang.t("ডেস্কটপ সাইট"),
                Lang.t("কম্পিউটারের মতো পেজ দেখায়"), desktopMode, new OnToggle() {
                    public void onChange(boolean on) {
                        desktopMode = on;
                        prefs.edit().putBoolean("desktop", desktopMode).apply();
                        applySettings();
                        if (current != null) current.reload();
                    }
                }));
        list.addView(themePicker());
        list.addView(toggleRow("translate", Lang.t("বাংলায় অনুবাদ"),
                Lang.t("ইংরেজি পেজ নিজে থেকেই বাংলায়"), autoTranslate, new OnToggle() {
                    public void onChange(boolean on) {
                        autoTranslate = on;
                        prefs.edit().putBoolean("autotranslate", autoTranslate).apply();
                        if (current != null) current.reload();
                    }
                }));

        list.addView(sectionLabel(Lang.t("ট্যাব")));

        list.addView(menuRow("delete", Lang.t("সব ট্যাব বন্ধ"),
                String.valueOf(tabs.size()), new Runnable() {
                    public void run() { confirmCloseAll(); }
                }));

        list.addView(sectionLabel(Lang.t("অ্যাপ")));

        list.addView(menuRow("public", Lang.t("ডিফল্ট ব্রাউজার"),
                isDefaultBrowser() ? Lang.t("সেট আছে") : Lang.t("সেট করুন"), new Runnable() {
                    public void run() { askDefaultBrowser(); }
                }));
        list.addView(menuRow("public", Lang.t("নেটওয়ার্ক পরীক্ষা"), null, new Runnable() {
            public void run() { networkCheck(); }
        }));
        list.addView(menuRow("settings", Lang.t("সেটিংস"), null, new Runnable() {
            public void run() {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        }));
        list.addView(menuRow("info", Lang.t("অ্যাপ সম্পর্কে"), VERSION, new Runnable() {
            public void run() { about(); }
        }));

        addDividers(list);
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
        slp.setMargins(dp(8), dp(8), dp(8), dp(8));
        box.addView(sheet, slp);

        openOverlay(box);
        sheet.setTranslationY(dp(48));
        sheet.setAlpha(0f);
        sheet.animate().translationY(0).alpha(1f).setDuration(190).start();
    }

    /** মেনুর ওপরে চারটি দ্রুত কাজ */
    private View quickActions(boolean marked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        row.addView(quickAction("add", Lang.t("নতুন ট্যাব"), new Runnable() {
            public void run() { newTab(homeUrl(), true); }
        }));
        row.addView(quickAction("refresh", Lang.t("রিলোড"), new Runnable() {
            public void run() { if (current != null) current.reload(); }
        }));
        row.addView(quickAction("share", Lang.t("শেয়ার"), new Runnable() {
            public void run() {
                if (current == null) return;
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, current.getUrl());
                startActivity(Intent.createChooser(i, Lang.t("শেয়ার করুন")));
            }
        }));
        row.addView(quickAction("star", Lang.t("বুকমার্ক"), new Runnable() {
            public void run() { toggleBookmark(); }
        }));
        return row;
    }

    private View quickAction(String iconName, String label, final Runnable action) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, dp(2), 0, dp(8));
        rippleRound(b, 18);

        LinearLayout circle = new LinearLayout(this);
        circle.setGravity(Gravity.CENTER);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(cAccentSoft);
        circle.setBackground(circleBg);
        ImageView g = icon(iconName, 24, cAccent);
        g.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
        circle.addView(g);
        b.addView(circle, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(10.5f);
        t.setTextColor(cMuted);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(2), dp(6), dp(2), 0);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.addView(t);

        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeOverlay();
                action.run();
            }
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return b;
    }

    /** দুই সারির মাঝে সরু রেখা, আইকনের ডান থেকে শুরু */
    private View menuDivider() {
        View line = new View(this);
        line.setBackgroundColor(cLine);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.leftMargin = dp(58);
        lp.rightMargin = dp(12);
        line.setLayoutParams(lp);
        return line;
    }

    /** পরপর দুটি সারির মাঝে রেখা বসায়; সেকশন লেখার পাশে বসায় না */
    private void addDividers(LinearLayout list) {
        for (int i = list.getChildCount() - 1; i > 0; i--) {
            View above = list.getChildAt(i - 1);
            View below = list.getChildAt(i);
            if (ROW_TAG.equals(above.getTag()) && ROW_TAG.equals(below.getTag())) {
                list.addView(menuDivider(), i);
            }
        }
    }

    private TextView sectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(cAccent);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(12), dp(18), dp(12), dp(6));
        return t;
    }

    private View menuRow(String icon, String label, String value, final Runnable action) {
        return menuRow(icon, label, null, value, action);
    }

    private View menuRow(String icon, String label, String sub, String value,
                         final Runnable action) {
        LinearLayout row = rowShell(icon, label, sub, value, true);
        row.setTag(ROW_TAG);
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

    private static final String ROW_TAG = "bb_row";

    private LinearLayout rowShell(String icon, String label, String sub,
                                  String value, boolean chevron) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(7), dp(8), dp(7));
        rippleRound(row, 15);

        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(rounded(cAccentSoft, 13));
        tile.addView(icon(icon, 23, cAccent));
        row.addView(tile, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(13), 0, dp(6), 0);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(15);
        name.setTextColor(cText);
        texts.addView(name);

        if (sub != null) {
            TextView subView = new TextView(this);
            subView.setText(sub);
            subView.setTextSize(11);
            subView.setTextColor(cMuted);
            subView.setPadding(0, dp(1), 0, 0);
            texts.addView(subView);
        }

        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (value != null) {
            TextView val = new TextView(this);
            val.setText(value);
            val.setTextSize(11.5f);
            val.setTextColor(cMuted);
            row.addView(val);
        }

        if (chevron) {
            TextView chev = new TextView(this);
            chev.setText("›");
            chev.setTextSize(17);
            chev.setTextColor(cMuted);
            chev.setPadding(dp(6), 0, dp(2), 0);
            row.addView(chev);
        }

        return row;
    }

    private interface OnToggle {
        void onChange(boolean checked);
    }

    /** সেটিংসের মতো আসল সুইচ, পাশে চালু/বন্ধ লেখা, নিচে ছোট করে লেখা কী করে। */
    private View toggleRow(String icon, String label, String sub, boolean on,
                           final OnToggle action) {
        final LinearLayout row = rowShell(icon, label, sub, null, false);
        row.setTag(ROW_TAG);

        final TextView state = new TextView(this);
        state.setText(Lang.onOff(on));
        state.setTextSize(10.5f);
        state.setTypeface(Typeface.DEFAULT_BOLD);
        state.setTextColor(on ? cAccent : cMuted);
        state.setPadding(0, 0, dp(8), 0);
        row.addView(state);

        final Switch sw = new Switch(this);
        sw.setTag("bb_switch");
        sw.setChecked(on);
        SwitchStyle.apply(sw, cAccent, darkMode);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                state.setText(Lang.onOff(checked));
                state.setTextColor(checked ? cAccent : cMuted);
                action.onChange(checked);
            }
        });
        row.addView(sw);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sw.toggle(); }
        });
        return row;
    }

    /** থিমের তিনটি রকম এক লাইনে, কোনটা চালু আছে সেটা স্পষ্ট দেখা যায়। */
    private View themePicker() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(2), 0, dp(6));
        wrap.setTag(ROW_TAG);

        wrap.addView(rowShell("theme", Lang.t("থিম"), 
                Lang.t("ওয়েবপেজও কালো হয়ে যায়"), null, false));

        final String[] keys = {T_SYSTEM, T_DARK, T_FORCE};
        final String[] names = {Lang.t("সিস্টেম"), Lang.t("রাতের"), Lang.t("কালো")};
        String cur = themeKey();

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(rounded(cSurface2, 15));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));

        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            boolean sel = keys[i].equals(cur);
            TextView chip = new TextView(this);
            chip.setText(names[i]);
            chip.setTextSize(12.5f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(4), dp(9), dp(4), dp(9));
            chip.setTextColor(sel ? (darkMode ? Color.parseColor("#04241B") : Color.WHITE) : cMuted);
            chip.setBackground(sel ? rounded(cAccent, 12) : null);
            chip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!key.equals(themeKey())) {
                        prefs.edit().putString("theme", key).apply();
                        recreate();
                    }
                }
            });
            seg.addView(chip, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(6);
        slp.rightMargin = dp(6);
        slp.topMargin = dp(2);
        wrap.addView(seg, slp);
        return wrap;
    }

    private View sheetHeader() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(6), dp(2), dp(4), dp(10));

        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(rounded(cAccentSoft, 14));
        ImageView logo = new ImageView(this);
        try {
            logo.setImageDrawable(getPackageManager().getApplicationIcon(getApplicationInfo()));
        } catch (Exception ignored) { }
        tile.addView(logo, new LinearLayout.LayoutParams(dp(30), dp(30)));
        head.addView(tile, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);

        TextView name = new TextView(this);
        name.setText(Lang.t("বাংলা ব্রাউজার"));
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(cText);
        texts.addView(name);

        TextView sub = new TextView(this);
        sub.setText(Lang.t("সংস্করণ") + " " + VERSION);
        sub.setTextSize(11);
        sub.setTextColor(cMuted);
        texts.addView(sub);

        head.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = glyphButton("✕", 15, cMuted);
        close.setPadding(dp(10), dp(8), dp(10), dp(8));
        rippleRound(close, 16);
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

        LinearLayout panel = fullPanel(Lang.t("ডাউনলোড"), new View.OnClickListener() {
            public void onClick(View v) {
                dialog().setTitle(Lang.t("তালিকা মুছবেন?"))
                        .setMessage(Lang.t("শুধু তালিকা থেকে মুছে যাবে, ফোনে নামানো ফাইল থাকবে।"))
                        .setPositiveButton(Lang.t("মুছুন"), (d, w) -> {
                            prefs.edit().putString("downloads", "[]").apply();
                            closeOverlay();
                            showDownloads();
                        })
                        .setNegativeButton(Lang.t("না"), null).show();
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
            host.addView(sectionLabel(Lang.t("চলছে (") + active.size() + ")"));
            for (Downloader.Task t : active) host.addView(activeRow(t));
        }

        JSONArray arr = downloadRecords();
        if (arr.length() > 0) {
            host.addView(sectionLabel(Lang.t("সম্পন্ন হয়েছে")));
            for (int i = 0; i < arr.length() && i < 100; i++) {
                try {
                    JSONObject o = arr.getJSONObject(i);
                    host.addView(recordRow(o.optString("n", Lang.t("ফাইল")),
                            o.optString("u", ""), o.optString("m", ""),
                            o.optLong("s", 0)));
                } catch (Exception ignored) { }
            }
        }

        if (active.isEmpty() && arr.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText(Lang.t("এখনো কোনো ডাউনলোড নেই।\nকোনো পেজ থেকে ফাইল নামালে সেটা এখানে দেখা যাবে।"));
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
                toast(Lang.t("ডাউনলোড বাতিল হচ্ছে"));
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
                    + Lang.t("/s · বাকি ") + Downloader.sizeText(left);
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
        sub.setText(Lang.t("সম্পন্ন হয়েছে") + (size > 0 ? " · " + Downloader.sizeText(size) : ""));
        sub.setTextSize(11);
        sub.setTextColor(cMuted);
        texts.addView(sub);

        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (uri.length() == 0) {
                    toast(Lang.t("ফাইলটা পাওয়া যায়নি"));
                    return;
                }
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(Uri.parse(uri), mime.length() == 0 ? "*/*" : mime);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(i);
                } catch (Exception e) {
                    toast(Lang.t("ফাইল খোলার মতো অ্যাপ নেই"));
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

        LinearLayout panel = fullPanel(Lang.t("ইতিহাস (") + items.size() + ")", new View.OnClickListener() {
            public void onClick(View v) {
                dialog().setTitle(Lang.t("ইতিহাস মুছবেন?"))
                        .setPositiveButton(Lang.t("মুছুন"), (d, w) -> {
                            saveList("history", new ArrayList<>());
                            closeOverlay();
                            showHistory();
                        })
                        .setNegativeButton(Lang.t("না"), null).show();
            }
        });

        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, 0, 0, dp(20));

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(Lang.t("ইতিহাস খালি।\nকোনো সাইটে গেলে সেটা এখানে জমা হবে।"));
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
                            .setItems(new String[]{Lang.t("নতুন ট্যাবে খুলুন"), Lang.t("লিংক কপি"), Lang.t("ইতিহাস থেকে মুছুন")},
                                    (d, which) -> {
                                        if (which == 0) {
                                            newTab(url, true);
                                        } else if (which == 1) {
                                            android.content.ClipboardManager cb =
                                                    (android.content.ClipboardManager)
                                                            getSystemService(CLIPBOARD_SERVICE);
                                            cb.setPrimaryClip(android.content.ClipData
                                                    .newPlainText(Lang.t("লিংক"), url));
                                            toast(Lang.t("লিংক কপি হয়েছে"));
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
        s.add(new String[]{Lang.t("প্রথম আলো"), "https://www.prothomalo.com"});
        s.add(new String[]{Lang.t("কালের কণ্ঠ"), "https://www.kalerkantho.com"});
        s.add(new String[]{Lang.t("বিডিনিউজ২৪"), "https://bangla.bdnews24.com"});
        s.add(new String[]{Lang.t("উইকিপিডিয়া"), "https://bn.wikipedia.org"});
        s.add(new String[]{Lang.t("ইউটিউব"), "https://m.youtube.com"});
        s.add(new String[]{Lang.t("ফেসবুক"), "https://m.facebook.com"});
        s.add(new String[]{Lang.t("জিমেইল"), "https://mail.google.com"});
        s.add(new String[]{Lang.t("বাংলানিউজ"), "https://www.banglanews24.com"});
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
                o.put("n", Lang.t(s[0]));
                o.put("u", s[1]);
                arr.put(o);
            } catch (Exception ignored) { }
        }
        view.evaluateJavascript("window.setSites && window.setSites(" + arr.toString() + ");", null);
    }

    private void showSiteEditor() {
        final List<String[]> sites = loadSites();

        LinearLayout panel = fullPanel(Lang.t("প্রিয় সাইট"), new View.OnClickListener() {
            public void onClick(View v) {
                dialog().setTitle(Lang.t("ডিফল্ট তালিকায় ফিরবেন?"))
                        .setPositiveButton(Lang.t("ফিরিয়ে আনো"), (d, w) -> {
                            saveList("sites", defaultSites());
                            closeOverlay();
                            showSiteEditor();
                            if (current != null) injectSites(current);
                        })
                        .setNegativeButton(Lang.t("না"), null).show();
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
            t.setText(Lang.t(name));
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
        plus.setText(Lang.t("+ নতুন সাইট যোগ করুন"));
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
        name.setHint(Lang.t("নাম (যেমন প্রথম আলো)"));
        name.setSingleLine(true);
        box.addView(name);

        final EditText url = new EditText(this);
        url.setHint(Lang.t("ঠিকানা (prothomalo.com)"));
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(url);

        dialog().setTitle(Lang.t("নতুন সাইট"))
                .setView(box)
                .setPositiveButton(Lang.t("যোগ করুন"), (d, w) -> {
                    String n = name.getText().toString().trim();
                    String u = url.getText().toString().trim();
                    if (u.length() == 0) {
                        toast(Lang.t("সাইটের ঠিকানা লিখো"));
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
                .setNegativeButton(Lang.t("বাতিল"), null)
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
            toast(Lang.t("বাংলা ব্রাউজার এখনই ডিফল্ট ব্রাউজার"));
            return;
        }
        if (!BrowserRole.request(this, REQ_ROLE)) {
            toast(Lang.t("এই ফোনে সেটিংস খোলা যায়নি"));
        }
    }

    // ---------------------------------------------------------------- page tools

    private void copyLink() {
        if (current == null) return;
        String u = current.getUrl();
        if (!isRealPage(u)) {
            toast(Lang.t("এই পেজের লিংক নেই"));
            return;
        }
        try {
            android.content.ClipboardManager cb =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(android.content.ClipData.newPlainText(Lang.t("লিংক"), u));
            toast(Lang.t("লিংক কপি হয়েছে"));
        } catch (Exception e) {
            toast(Lang.t("কপি করা যায়নি"));
        }
    }

    private void findInPage() {
        if (current == null) return;
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setHint(Lang.t("পেজে লেখা খুঁজুন"));
        input.setTextColor(cText);
        input.setHintTextColor(cMuted);
        input.setPadding(dp(20), dp(10), dp(20), dp(10));

        dialog().setTitle(Lang.t("পেজে খুঁজুন"))
                .setView(input)
                .setPositiveButton(Lang.t("খুঁজুন"), (d, w) -> {
                    String q = input.getText().toString();
                    if (q.length() > 0) current.findAllAsync(q);
                })
                .setNegativeButton(Lang.t("বন্ধ"), (d, w) -> {
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
            if (name.length() == 0) name = Lang.t("বাংলা ব্রাউজার");
            pm.print(name, current.createPrintDocumentAdapter(name), null);
        } catch (Exception e) {
            toast(Lang.t("এই ফোনে প্রিন্ট চালু নেই"));
        }
    }

    private void closeAllTabs() {
        for (Tab t : new ArrayList<>(tabs)) {
            try {
                if (t.web != null) {
                    holder.removeView(t.web);
                    t.web.stopLoading();
                    t.web.destroy();
                }
            } catch (Exception ignored) { }
        }
        tabs.clear();
        activeIndex = 0;
        current = null;
        newTab(homeUrl(), true);
        toast(Lang.t("সব ট্যাব বন্ধ হয়েছে"));
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
                if (view == current) setReloadState(true);
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
                if (view == current) {
                    setReloadState(false);
                    updateNavButtons();
                }
                if (t != null && !isAdUrl(url)) t.lastGood = url;
                if (isRealPage(url) && !isAdUrl(url)) addHistory(t == null ? null : t.title, url);
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
                setReloadState(false);
                if (recoverFromAd(view, failingUrl)) return;
                showErrorPage(view, failingUrl, code, desc);
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request == null || !request.isForMainFrame()) return;
                String failingUrl = request.getUrl() == null ? null : request.getUrl().toString();
                if (!isRealPage(failingUrl) || view != current) return;
                progress.setVisibility(View.GONE);
                setReloadState(false);
                if (recoverFromAd(view, failingUrl)) return;
                if (tryPlainHttp(view, failingUrl)) return;
                int code = error == null ? 0 : error.getErrorCode();
                String desc = error == null || error.getDescription() == null
                        ? "" : error.getDescription().toString();
                showErrorPage(view, failingUrl, code, desc);
            }

            @Override
            public void onReceivedSslError(WebView view, final android.webkit.SslErrorHandler handler,
                                           android.net.http.SslError error) {
                progress.setVisibility(View.GONE);
                setReloadState(false);
                String errUrl = error == null ? null : error.getUrl();
                String pageUrl = view.getUrl();
                String errHost = errUrl == null ? "" : String.valueOf(Uri.parse(errUrl).getHost());
                String pageHost = pageUrl == null ? "" : String.valueOf(Uri.parse(pageUrl).getHost());
                // শুধু মূল পেজের সার্টিফিকেট সমস্যাতেই জিজ্ঞাসা, বিজ্ঞাপন/ছবির জন্য নয়
                if (view != current || !errHost.equalsIgnoreCase(pageHost)) {
                    handler.cancel();
                    return;
                }
                dialog()
                        .setTitle(Lang.t("সার্টিফিকেট সমস্যা"))
                        .setMessage(Lang.t("এই সাইটের নিরাপত্তা সার্টিফিকেট ঠিক নেই। "
                                + "তবুও খুললে তথ্য ঝুঁকিতে থাকতে পারে।"))
                        .setPositiveButton(Lang.t("তবুও খুলুন"), (d, w) -> handler.proceed())
                        .setNegativeButton(Lang.t("বাতিল"), (d, w) -> handler.cancel())
                        .setOnCancelListener(d -> handler.cancel())
                        .show();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (view != current) return;
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                if (newProgress >= 100) setReloadState(false);
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
                    startActivityForResult(Intent.createChooser(i, Lang.t("ফাইল বাছুন")), REQ_FILE);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          android.os.Message resultMsg) {
                // window.open() (বিজ্ঞাপনের ফাঁদ) বর্তমান ট্যাব দখল করতে পারবে না
                if (!isUserGesture) return false;
                final WebView popup = new WebView(MainActivity.this);
                popup.getSettings().setJavaScriptEnabled(true);
                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        openPopup(url);
                        return true;
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v,
                                                            android.webkit.WebResourceRequest req) {
                        openPopup(req.getUrl() == null ? null : req.getUrl().toString());
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
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

    /** পপআপের ঠিকানা: বিজ্ঞাপন বাদ দিয়ে নতুন ট্যাবে */
    private void openPopup(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
        String host = Uri.parse(url).getHost();
        host = host == null ? "" : host.toLowerCase(java.util.Locale.US);
        if (host.contains("googleadservices") || host.contains("googlesyndication")
                || host.contains("doubleclick")) return;
        newTab(url, true);
    }

    private void applySettings() {
        String engine = prefs.getString("engine", "google");
        int zoom = prefs.getInt("textzoom", 100);
        boolean js = prefs.getBoolean("js", true);
        boolean images = prefs.getBoolean("images", true);
        for (Tab t : tabs) {
            if (t.web == null) continue;
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
            toast(isDefaultBrowser() ? Lang.t("ডিফল্ট ব্রাউজার সেট হয়েছে") : Lang.t("ডিফল্ট ব্রাউজার সেট হয়নি"));
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
        String hash = "lang=" + (Lang.isEn() ? "en" : "bn");
        if (darkMode) hash = hash + "&dark";
        return HOME_URL + "#" + hash;
    }

    private static final String[] AD_HOSTS = {
            "googleadservices", "googlesyndication", "doubleclick", "adservice.google",
            "pagead2", "adnxs", "taboola", "outbrain", "popads", "propellerads",
            "onclickads", "adsterra", "revcontent", "zedo", "adcash", "trafficjunky"
    };

    /** বিজ্ঞাপন/ট্র্যাকিং ঠিকানা — এসব পেজ হিসেবে খোলা হয় না */
    private boolean isAdUrl(String url) {
        if (url == null || url.length() == 0) return false;
        String host;
        try {
            host = Uri.parse(url).getHost();
        } catch (Exception e) {
            return false;
        }
        if (host == null) return false;
        host = host.toLowerCase(java.util.Locale.US);
        for (String bad : AD_HOSTS) {
            if (host.contains(bad)) return true;
        }
        return false;
    }

    private boolean handleUrl(WebView view, String url) {
        if (url == null) return true;
        if (isAdUrl(url)) return true;   // বিজ্ঞাপন যেন পেজ দখল না করে
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
                String text = q.trim();
                boolean site = looksLikeAddress(text);
                String target;
                if (site) {
                    target = (text.startsWith("http://") || text.startsWith("https://"))
                            ? text : "https://" + text;
                } else {
                    target = searchUrl(text);
                }
                view.loadUrl(target);
                addHistory(text, target);
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

    /** বিজ্ঞাপনের ঠিকানায় আটকে গেলে আগের ভালো পেজে ফিরে আসে */
    private boolean recoverFromAd(WebView view, String failingUrl) {
        if (!isAdUrl(failingUrl)) return false;
        Tab t = tabOf(view);
        String back = (t != null && t.lastGood != null && t.lastGood.length() > 0)
                ? t.lastGood : homeUrl();
        view.loadUrl(back);
        return true;
    }

    /**
     * সাইট যদি শুধু http-তে চলে (সার্টিফিকেট নেই বা https বন্ধ), একবার সাদা http দিয়ে
     * আবার চেষ্টা করে। একই ঠিকানায় দুবার চেষ্টা করে না, তাই লুপ হয় না।
     */
    private boolean tryPlainHttp(WebView view, String url) {
        if (url == null || !url.startsWith("https://") || view != current) return false;
        String plain = "http://" + url.substring(8);
        if (!httpTried.add(plain)) return false;
        progress.setVisibility(View.VISIBLE);
        view.loadUrl(plain);
        return true;
    }

    /** লেখাটা ঠিকানা না সার্চের কথা — হোম পাতার বাক্স থেকেও সাইট খোলার জন্য */
    private boolean looksLikeAddress(String text) {
        if (text == null) return false;
        String x = text.trim();
        if (x.length() == 0 || x.indexOf(' ') >= 0) return false;
        if (x.startsWith("http://") || x.startsWith("https://")) return true;
        return x.matches("[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+\\.[a-zA-Z]{2,}(/.*)?");
    }

    private void go(String input) {
        hideSuggestions();
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

    /** লোড চলার সময় ✕ (থামানোর জন্য), শেষ হলে ↻ (রিফ্রেশ) */
    private void setReloadState(boolean loading) {
        pageLoading = loading;
        if (reloadIcon == null) return;
        int id = getResources().getIdentifier(loading ? "ic_close" : "ic_refresh",
                "drawable", getPackageName());
        if (id != 0) reloadIcon.setImageResource(id);
        reloadIcon.animate().cancel();
        reloadIcon.setRotation(0f);
    }

    private void hideSuggestions() {
        if (suggestBox == null) return;
        suggestSeq++;
        cancelSuggestFetch();
        suggestQuery = "";
        suggestRemoteQuery = "";
        suggestRemote = new ArrayList<String>();
        suggestBox.removeAllViews();
        suggestBox.setVisibility(View.GONE);
    }

    /** লেখার সময় নিচে সাজেশন: ঠিকানা, সার্চ, গুগলের সাজেশন, আর আগে দেখা সাইট */
    private void showSuggestions(String text) {
        if (suggestBox == null) return;
        String q = text == null ? "" : text.trim();
        suggestQuery = q;
        // লেখা বদলালে আগের শব্দের সাজেশন আর দেখাব না
        if (!q.equalsIgnoreCase(suggestRemoteQuery)) suggestRemote = new ArrayList<String>();

        if (q.length() == 0) {
            suggestSeq++;               // আগের রিকোয়েস্টের ফল বাতিল
            cancelSuggestFetch();
            suggestRemote = new ArrayList<String>();
            renderSuggestions();
            return;
        }

        renderSuggestions();
        scheduleFetch(q);
    }

    /** লেখা থামলে ২২০ মিলিসেকেন্ড পরে গুগলের কাছে সাজেশন চাই */
    private void scheduleFetch(final String q) {
        cancelSuggestFetch();
        final int seq = ++suggestSeq;
        suggestPending = new Runnable() {
            public void run() {
                suggestPending = null;
                fetchSuggestions(q, seq);
            }
        };
        suggestHandler.postDelayed(suggestPending, 220);
    }

    private void cancelSuggestFetch() {
        if (suggestPending != null) {
            suggestHandler.removeCallbacks(suggestPending);
            suggestPending = null;
        }
    }

    /** গুগলের suggest API — শুধু লেখা, কোনো ব্যক্তিগত তথ্য পাঠায় না */
    private void fetchSuggestions(final String q, final int seq) {
        new Thread(new Runnable() {
            public void run() {
                final List<String> out = new ArrayList<String>();
                java.net.HttpURLConnection c = null;
                try {
                    String url = "https://suggestqueries.google.com/complete/search?client=firefox&q="
                            + enc(q);
                    c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(6000);
                    c.setReadTimeout(6000);
                    c.setRequestProperty("User-Agent",
                            uaMobile == null ? "Mozilla/5.0" : uaMobile);
                    java.io.InputStream in = c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        bo.write(buf, 0, n);
                        if (bo.size() > 200000) break;
                    }
                    in.close();
                    JSONArray arr = new JSONArray(new String(bo.toByteArray(), "UTF-8"));
                    if (arr.length() > 1) {
                        JSONArray list = arr.optJSONArray(1);
                        if (list != null) {
                            for (int i = 0; i < list.length() && i < 8; i++) {
                                String term = list.optString(i, "");
                                if (term.length() > 0) out.add(term);
                            }
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (c != null) c.disconnect();
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (seq != suggestSeq) return;
                        if (!q.equalsIgnoreCase(suggestQuery)) return;
                        suggestRemote = out;
                        suggestRemoteQuery = q;
                        renderSuggestions();
                    }
                });
            }
        }).start();
    }

    /** তালিকাটা নতুন করে সাজায়; ইন্টারনেটের ফল আসলে আবার ডাকা হয় */
    private void renderSuggestions() {
        if (suggestBox == null) return;
        suggestBox.removeAllViews();
        String q = suggestQuery == null ? "" : suggestQuery;

        if (q.length() == 0) {
            // খালি বারে চাপ দিলে সাম্প্রতিক সাইট
            int recent = 0;
            for (String[] item : loadList("history")) {
                if (recent >= 5) break;
                if (item == null || item.length < 2 || item[1] == null) continue;
                String title = item[0] == null ? "" : item[0];
                addSuggestRow(recent == 0 ? "history" : "history",
                        title.length() == 0 ? prettyUrl(item[1]) : title, item[1], item[1]);
                recent++;
            }
            if (recent == 0) {
                suggestBox.setVisibility(View.GONE);
                return;
            }
            trimLastDivider();
            suggestBox.setVisibility(View.VISIBLE);
            return;
        }

        int rows = 0;
        if (looksLikeAddress(q)) {
            addSuggestRow("public", q, null, q);
            rows++;
        }
        addSuggestRow("search", Lang.t("সার্চ") + ": " + q, null, q);
        rows++;

        // গুগলের আসল সাজেশন
        for (String term : suggestRemote) {
            if (rows >= 9) break;
            if (term == null || term.length() == 0) continue;
            if (term.equalsIgnoreCase(q)) continue;
            addSuggestRow("search", term, null, term);
            rows++;
        }

        // আগে দেখা সাইট
        for (String[] item : loadList("history")) {
            if (rows >= 9) break;
            if (item == null || item.length < 2 || item[1] == null) continue;
            String title = item[0] == null ? "" : item[0];
            String url = item[1];
            if (url.equals(q)) continue;
            if (!contains(title, q) && !contains(url, q) && !contains(prettyUrl(url), q)) continue;
            addSuggestRow("history", title.length() == 0 ? prettyUrl(url) : title, url, url);
            rows++;
        }

        // বুকমার্ক
        for (String[] item : loadList("bookmarks")) {
            if (rows >= 9) break;
            if (item == null || item.length < 2 || item[1] == null) continue;
            String title = item[0] == null ? "" : item[0];
            String url = item[1];
            if (url.equals(q)) continue;
            if (!contains(title, q) && !contains(url, q) && !contains(prettyUrl(url), q)) continue;
            addSuggestRow("star", title.length() == 0 ? prettyUrl(url) : title, url, url);
            rows++;
        }

        if (rows == 0) {
            suggestBox.setVisibility(View.GONE);
            return;
        }
        trimLastDivider();
        suggestBox.setVisibility(View.VISIBLE);
    }

    /** শেষ সারির নিচে বাড়তি রেখা থাকবে না */
    private void trimLastDivider() {
        int n = suggestBox.getChildCount();
        if (n > 0) suggestBox.removeViewAt(n - 1);
    }

    private boolean contains(String hay, String needle) {
        return hay != null && needle != null
                && hay.toLowerCase(java.util.Locale.US).contains(needle.toLowerCase(java.util.Locale.US));
    }

    private void addSuggestRow(String iconName, String title, String sub, final String target) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(9), dp(16), dp(9));

        row.addView(icon(iconName, 18, cMuted), new LinearLayout.LayoutParams(dp(18), dp(18)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(13), 0, 0, 0);

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(14);
        name.setTextColor(cText);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name);

        if (sub != null) {
            TextView url = new TextView(this);
            url.setText(prettyUrl(sub));
            url.setTextSize(11);
            url.setTextColor(cMuted);
            url.setSingleLine(true);
            url.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(url);
        }

        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                hideSuggestions();
                hideKeyboard();
                urlBar.clearFocus();
                go(target);
            }
        });
        suggestBox.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View line = new View(this);
        line.setBackgroundColor(cLine);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lineLp.leftMargin = dp(46);
        suggestBox.addView(line, lineLp);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
            urlBar.clearFocus();
        } catch (Exception ignored) { }
    }

    private void showErrorPage(WebView view, String url) {
        showErrorPage(view, url, 0, "");
    }

    private void showErrorPage(WebView view, String url, int code, String desc) {
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:" + (darkMode ? "#080B0A" : "#FFFFFF")
                + ";color:" + (darkMode ? "#EAF1EE" : "#0D1412") + ";padding:40px 24px;text-align:center}"
                + "h2{font-size:18px;margin:0 0 8px}p{font-size:13px;color:#8D9A95;word-break:break-all}"
                + "a{display:inline-block;margin-top:20px;background:" + (darkMode ? "#34D399" : "#007A5A")
                + ";color:" + (darkMode ? "#04241B" : "#fff")
                + ";text-decoration:none;padding:12px 22px;border-radius:16px;font-size:14px}</style>"
                + Lang.t("<body><div style='font-size:40px'>📡</div><h2>পেজ লোড হয়নি</h2>")
                + Lang.t("<p>ইন্টারনেট সংযোগ দেখুন, তারপর আবার চেষ্টা করুন।</p>")
                + "<p style='font-size:11px'>" + url + "</p>"
                + (code == 0 ? "" : "<p style='font-size:11px;color:#8D9A95'>"
                        + Lang.t("ত্রুটি") + " " + code + (desc.length() == 0 ? "" : " · " + desc) + "</p>")
                + Lang.t("<a href='bangla://reload'>আবার চেষ্টা করুন</a></body></html>");
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
            toast(Lang.t("এই পেজ বুকমার্ক করা যায় না"));
            return;
        }
        List<String[]> b = loadList("bookmarks");
        for (int i = 0; i < b.size(); i++) {
            if (b.get(i)[1].equals(url)) {
                b.remove(i);
                saveList("bookmarks", b);
                updateNavButtons();
                toast(Lang.t("বুকমার্ক সরানো হয়েছে"));
                return;
            }
        }
        String t = pageTitle.getText().toString();
        if (t.length() == 0) t = prettyUrl(url);
        b.add(0, new String[]{t, url});
        saveList("bookmarks", b);
        updateNavButtons();
        toast(Lang.t("বুকমার্কে রাখা হয়েছে"));
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
            toast(Lang.t("এখনো কিছু নেই"));
            return;
        }
        String[] names = new String[items.size()];
        for (int i = 0; i < items.size(); i++) names[i] = items.get(i)[0];
        AlertDialog.Builder b = dialog().setTitle(title)
                .setItems(names, (d, which) -> {
                    if (current != null) current.loadUrl(items.get(which)[1]);
                });
        if (clearable) {
            b.setNegativeButton(Lang.t("সব মুছুন"), (d, w) -> {
                saveList(key, new ArrayList<>());
                toast(Lang.t("মুছে ফেলা হয়েছে"));
            });
        }
        b.setPositiveButton(Lang.t("বন্ধ"), null).show();
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

    /** ইন্টারনেট, সার্টিফিকেট আর WebView-এর অবস্থা এক পাতায় দেখায় */
    private void networkCheck() {
        String wv = "";
        try {
            wv = getPackageManager().getPackageInfo("com.google.android.webview", 0).versionName;
        } catch (Exception ignored) { }
        final StringBuilder head = new StringBuilder();
        head.append("Android ").append(Build.VERSION.RELEASE)
                .append(" · SDK ").append(Build.VERSION.SDK_INT)
                .append("\nWebView ").append(wv.length() == 0 ? "?" : wv)
                .append("\n\n");

        final AlertDialog dlg = dialog()
                .setTitle(Lang.t("নেটওয়ার্ক পরীক্ষা"))
                .setMessage(head.toString() + Lang.t("দেখা হচ্ছে…"))
                .setPositiveButton(Lang.t("ঠিক আছে"), null)
                .show();

        final String[] urls = {"https://www.google.com/", "https://www.prothomalo.com/",
                "http://example.com/"};
        new Thread(new Runnable() {
            public void run() {
                final StringBuilder res = new StringBuilder(head);
                for (int i = 0; i < urls.length; i++) {
                    res.append(check(urls[i])).append("\n");
                    final String text = res.toString();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            try { dlg.setMessage(text); } catch (Exception ignored) { }
                        }
                    });
                }
            }
        }).start();
    }

    private String check(String url) {
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(9000);
            c.setReadTimeout(9000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", uaMobile == null ? "BanglaBrowser" : uaMobile);
            int code = c.getResponseCode();
            return url + "  →  " + code;
        } catch (Exception e) {
            String m = e.getMessage();
            if (m != null && m.length() > 70) m = m.substring(0, 70) + "…";
            return url + "  →  " + e.getClass().getSimpleName()
                    + (m == null ? "" : ": " + m);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void about() {
        String msg = Lang.isEn()
                ? "Bangla Browser\nVersion " + VERSION + "\n\n"
                + "Simple, fast and fully Bengali.\n"
                + "Tabs, bookmarks, history, dark mode — all in one.\n"
                + "English pages turn into Bengali on their own.\n\n"
                + "Developer\n"
                + "Mamun (@Mamun015)\n"
                + "github.com/Mamun015\n\n"
                + "Source code: open source\n"
                + "License: Mozilla Public License 2.0 (MPL-2.0)\n"
                + "github.com/Mamun015/bangla-browser\n\n"
                + "Made in Bangladesh"
                : "বাংলা ব্রাউজার\nসংস্করণ " + VERSION + "\n\n"
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
        dialog().setTitle(Lang.t("অ্যাপ সম্পর্কে"))
                .setMessage(msg)
                .setPositiveButton(Lang.t("ঠিক আছে"), null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
