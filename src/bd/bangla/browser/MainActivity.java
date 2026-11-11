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
    private static final String UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final int MAX_TABS = 8;

    private WebView current;
    private EditText urlBar;
    private ProgressBar progress;
    private TextView btnBack, btnForward, btnStar, pageTitle, tabBadge;
    private LinearLayout rootView, bottomBar;
    private FrameLayout screen, holder;
    private SharedPreferences prefs;
    private View overlay;

    private final List<Tab> tabs = new ArrayList<>();
    private int activeIndex = 0;

    private boolean darkMode, desktopMode;
    private String uaMobile;
    private int cBg, cSurface, cText, cMuted, cAccent, cLine;
    private long lastBackPress = 0;

    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_FILE = 4201;

    private static class Tab {
        WebView web;
        String title = "";
        String url = "";
    }

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
        boolean dark = prefs.getBoolean("dark", false);
        boolean desk = prefs.getBoolean("desktop", false);
        if (dark != darkMode) {
            recreate();
            return;
        }
        applySettings();
        if (desk != desktopMode) {
            desktopMode = desk;
            if (current != null) current.reload();
        }
    }

    @Override
    protected void onDestroy() {
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
        if (overlay != null) {
            closeOverlay();
            return;
        }
        if (current != null && current.canGoBack()) {
            current.goBack();
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

        TextView menuBtn = glyphButton("⋮", 20, cText);
        titleRow.addView(menuBtn);
        menuBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showMenu(); }
        });

        topBar.addView(titleRow);

        // address row
        LinearLayout addrPill = new LinearLayout(this);
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
        btnStar = navItem("☆", "বুকমার্ক", new Runnable() {
            public void run() { toggleBookmark(); }
        });
        navItem("▣", "ট্যাব", new Runnable() {
            public void run() { showTabSwitcher(); }
        });

        rootView.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        screen.addView(rootView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(screen);
        updateNavButtons();
    }

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

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(9);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(cMuted);
        t.setPadding(0, dp(2), 0, 0);
        box.addView(t);

        bottomBar.addView(box, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return g;
    }

    private void updateNavButtons() {
        if (current == null) return;
        btnBack.setAlpha(current.canGoBack() ? 1f : 0.3f);
        btnForward.setAlpha(current.canGoForward() ? 1f : 0.3f);
        boolean marked = isBookmarked(current.getUrl());
        btnStar.setText(marked ? "★" : "☆");
        btnStar.setTextColor(marked ? cAccent : cText);
        updateTabBadge();
    }

    private void updateTabBadge() {
        tabBadge.setText(String.valueOf(tabs.size()));
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

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        list.addView(menuRow("➕", "নতুন ট্যাব", null, new Runnable() {
            public void run() { newTab(homeUrl(), true); }
        }));
        list.addView(menuRow("⭐", "বুকমার্ক", String.valueOf(loadList("bookmarks").size()), new Runnable() {
            public void run() { showList("বুকমার্ক", "bookmarks", true); }
        }));
        list.addView(menuRow("🕘", "ইতিহাস", null, new Runnable() {
            public void run() { showList("ইতিহাস", "history", true); }
        }));
        list.addView(menuRow("⬇️", "ডাউনলোড", null, new Runnable() {
            public void run() {
                try {
                    startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));
                } catch (Exception e) {
                    toast("ডাউনলোড অ্যাপ পাওয়া যায়নি");
                }
            }
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

        list.addView(sectionLabel("পেজ"));

        list.addView(toggleRow(desktopMode ? "🖥️" : "📱",
                "ডেস্কটপ সাইট", desktopMode, new Runnable() {
                    public void run() {
                        desktopMode = !desktopMode;
                        prefs.edit().putBoolean("desktop", desktopMode).apply();
                        applySettings();
                        if (current != null) current.reload();
                        showMenu();
                    }
                }));

        list.addView(toggleRow(darkMode ? "🌙" : "☀️", "রাতের মোড", darkMode, new Runnable() {
            public void run() {
                darkMode = !darkMode;
                prefs.edit().putBoolean("dark", darkMode).apply();
                recreate();
            }
        }));

        list.addView(sectionLabel("অ্যাপ"));

        list.addView(menuRow("⚙️", "সেটিংস", null, new Runnable() {
            public void run() {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        }));
        list.addView(menuRow("ℹ️", "অ্যাপ সম্পর্কে", "১.১.০২", new Runnable() {
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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(12), dp(8));
        if (action != null) {
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            if (tv.resourceId != 0) row.setBackgroundResource(tv.resourceId);
        }

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

    private View toggleRow(String icon, String label, boolean on, final Runnable action) {
        return menuRow(icon, label, on ? "চালু" : "বন্ধ", action);
    }

    // ---------------------------------------------------------------- overlays

    private void openOverlay(View v) {
        closeOverlay();
        overlay = v;
        screen.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void closeOverlay() {
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
                if (url != null && url.startsWith(HOME_URL)) injectRecent(view);
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
        h.add(0, new String[]{title == null || title.length() == 0 ? url : title, url});
        while (h.size() > 40) h.remove(h.size() - 1);
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

    private void about() {
        String msg = "বাংলা ব্রাউজার\nসংস্করণ ১.১.০২\n\n"
                + "সহজ, দ্রুত ও সম্পূর্ণ বাংলা ব্রাউজার।\n"
                + "ট্যাব, বুকমার্ক, ইতিহাস, রাতের মোড — সব একসাথে।\n\n"
                + "নির্মাতা\n"
                + "মামুন\n"
                + "গিটহাব: github.com/mamun0937\n\n"
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
