package bd.bangla.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {

    private static final String PREFS = "bangla_browser";
    private static final int REQ_ROLE = 4301;

    private SharedPreferences prefs;
    private boolean darkMode;
    private int cBg, cSurface, cSurface2, cText, cMuted, cAccent, cAccentSoft, cLine;
    private static final String VERSION = "2.0";
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Lang.init(prefs);
        darkMode = themeIsDark();
        pickColors();

        if (Build.VERSION.SDK_INT >= 21) {
            Window w = getWindow();
            w.setStatusBarColor(cSurface);
            w.setNavigationBarColor(cBg);
            if (Build.VERSION.SDK_INT >= 23 && !darkMode) {
                w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(cBg));

        build();
    }

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

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private void ripple(View v) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        if (tv.resourceId != 0) v.setBackgroundResource(tv.resourceId);
    }

    /** সারির আইকন: res/drawable-এর ভেক্টর, "Aa" হলে লেখা */
    private View rowIcon(String key) {
        if ("Aa".equals(key)) {
            TextView t = new TextView(this);
            t.setText("Aa");
            t.setTextSize(14);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(cAccent);
            t.setGravity(Gravity.CENTER);
            return t;
        }
        ImageView v = new ImageView(this);
        int id = getResources().getIdentifier("ic_" + key, "drawable", getPackageName());
        if (id != 0) {
            v.setImageResource(id);
            v.setColorFilter(cAccent);
        }
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    private void rowBg(View v, boolean pressable) {
        GradientDrawable base = rounded(cSurface, 12);
        if (pressable) {
            int tint = darkMode ? 0x332BBD8F : 0x22006A4E;
            v.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(tint), base, base));
        } else {
            v.setBackground(base);
        }
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(cBg);
        top.setPadding(dp(6), dp(10), dp(16), dp(10));

        FrameLayout backBox = new FrameLayout(this);
        backBox.setPadding(dp(8), dp(6), dp(8), dp(6));
        ImageView back = new ImageView(this);
        int backId = getResources().getIdentifier("ic_back", "drawable", getPackageName());
        if (backId != 0) {
            back.setImageResource(backId);
            back.setColorFilter(cText);
        }
        back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        backBox.addView(back, new FrameLayout.LayoutParams(dp(24), dp(24)));
        backBox.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        top.addView(backBox);

        ImageView logo = new ImageView(this);
        try {
            logo.setImageDrawable(getPackageManager().getApplicationIcon(getApplicationInfo()));
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(26), dp(26));
        llp.leftMargin = dp(2);
        llp.rightMargin = dp(10);
        top.addView(logo, llp);

        TextView title = new TextView(this);
        title.setText(Lang.t("সেটিংস"));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(cText);
        top.addView(title);

        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(4), dp(12), dp(28));

        list.addView(section(Lang.t("সাধারণ")));
        list.addView(row("public", Lang.t("ভাষা"),
                Lang.isEn() ? Lang.t("ইংরেজি") : Lang.t("বাংলা"), new Runnable() {
                    public void run() { pickLanguage(); }
                }));
        list.addView(row("search", Lang.t("সার্চ ইঞ্জিন"), engineName(), new Runnable() {
            public void run() { pickEngine(); }
        }));
        list.addView(row("Aa", Lang.t("লেখার আকার"), textZoomName(), new Runnable() {
            public void run() { pickZoom(); }
        }));

        list.addView(section(Lang.t("চেহারা")));
        list.addView(row("theme", Lang.t("থিম"), themeName(), new Runnable() {
            public void run() { pickTheme(); }
        }));
        list.addView(switchRow("desktop", Lang.t("ডেস্কটপ সাইট"), prefs.getBoolean("desktop", false), "desktop"));

        list.addView(section(Lang.t("ব্রাউজিং")));
        list.addView(switchRow("translate", Lang.t("বাংলায় অনুবাদ"), prefs.getBoolean("autotranslate", true), "autotranslate"));
        list.addView(switchRow("code", Lang.t("জাভাস্ক্রিপ্ট"), prefs.getBoolean("js", true), "js"));
        list.addView(switchRow("image", Lang.t("ছবি লোড"), prefs.getBoolean("images", true), "images"));
        list.addView(switchRow("tab", Lang.t("লিংক নতুন ট্যাবে"), prefs.getBoolean("newtab", false), "newtab"));
        list.addView(switchRow("history", Lang.t("শেষ ট্যাব ফিরিয়ে আনুন"),
                Lang.t("ব্রাউজার চালু হলে আগের ট্যাব ফিরে আসে"),
                prefs.getBoolean("restore", true), "restore"));

        list.addView(section(Lang.t("ডেটা")));
        list.addView(row("history", Lang.t("ইতিহাস মুছুন"), null, new Runnable() {
            public void run() { clearData("history", Lang.t("ইতিহাস")); }
        }));
        list.addView(row("bookmarks", Lang.t("বুকমার্ক মুছুন"), null, new Runnable() {
            public void run() { clearData("bookmarks", Lang.t("বুকমার্ক")); }
        }));
        list.addView(row("cookie", Lang.t("কুকি ও ক্যাশে মুছুন"), null, new Runnable() {
            public void run() { clearCookies(); }
        }));
        list.addView(row("delete", Lang.t("সব ডেটা মুছুন"), null, new Runnable() {
            public void run() { clearAll(); }
        }));

        list.addView(section(Lang.t("অ্যাপ")));
        list.addView(row("public", Lang.t("ডিফল্ট ব্রাউজার"),
                BrowserRole.isDefault(this) ? Lang.t("সেট আছে") : Lang.t("সেট করুন"), new Runnable() {
                    public void run() {
                        if (!BrowserRole.request(SettingsActivity.this, REQ_ROLE)) {
                            toast(Lang.t("এই ফোনে সেটিংস খোলা যায়নি"));
                        }
                    }
                }));
        list.addView(row("download", Lang.t("ডাউনলোড দেখুন"), null, new Runnable() {
            public void run() {
                try {
                    startActivity(new android.content.Intent(
                            android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));
                } catch (Exception e) {
                    toast(Lang.t("ডাউনলোড অ্যাপ পাওয়া যায়নি"));
                }
            }
        }));
        list.addView(row("info", Lang.t("সংস্করণ"), VERSION, null));
        list.addView(row("link", Lang.t("সোর্স কোড"), "GitHub", new Runnable() {
            public void run() {
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/Mamun015/bangla-browser")));
                } catch (Exception ignored) { }
            }
        }));

        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(cMuted);
        t.setPadding(dp(8), dp(18), dp(8), dp(6));
        return t;
    }

    private View row(String icon, String label, String value, final Runnable action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), dp(13), dp(14), dp(13));
        rowBg(box, action != null);

        box.addView(rowIcon(icon), new LinearLayout.LayoutParams(dp(26), dp(24)));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(15);
        name.setTextColor(cText);
        name.setPadding(dp(14), 0, 0, 0);
        box.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (value != null) {
            TextView val = new TextView(this);
            val.setText(value);
            val.setTextSize(12);
            val.setTextColor(cMuted);
            box.addView(val);
        }

        if (action != null) {
            box.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { action.run(); }
            });
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        box.setLayoutParams(lp);
        return box;
    }

    private View switchRow(String icon, String label, boolean on, final String key) {
        return switchRow(icon, label, null, on, key);
    }

    private View switchRow(String icon, String label, String sub, boolean on, final String key) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), dp(9), dp(14), dp(9));
        rowBg(box, true);

        box.addView(rowIcon(icon), new LinearLayout.LayoutParams(dp(26), dp(24)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, 0, 0);

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

        box.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView state = new TextView(this);
        state.setText(Lang.onOff(on));
        state.setTextSize(10.5f);
        state.setTypeface(Typeface.DEFAULT_BOLD);
        state.setTextColor(on ? cAccent : cMuted);
        state.setPadding(0, 0, dp(8), 0);
        box.addView(state);

        final Switch sw = new Switch(this);
        sw.setChecked(on);
        SwitchStyle.apply(sw, cAccent, darkMode);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                state.setText(Lang.onOff(checked));
                state.setTextColor(checked ? cAccent : cMuted);
                prefs.edit().putBoolean(key, checked).apply();
                if (key.equals("dark") && checked != darkMode) {
                    recreate();
                }
            }
        });
        box.addView(sw);

        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sw.toggle(); }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        box.setLayoutParams(lp);
        return box;
    }

    // ---------------------------------------------------------------- pickers

    private void pickLanguage() {
        final String[] names = {Lang.t("বাংলা"), Lang.t("ইংরেজি")};
        final String[] keys = {"bn", "en"};
        int checked = Lang.isEn() ? 1 : 0;
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(Lang.t("অ্যাপের ভাষা"))
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    prefs.edit().putString("lang", keys[which]).apply();
                    Lang.init(prefs);
                    d.dismiss();
                    recreate();
                })
                .show();
    }

    private int dialogTheme() {
        return darkMode ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert;
    }

    private String engineName() {
        String e = prefs.getString("engine", "google");
        if (e.equals("ddg")) return "DuckDuckGo";
        if (e.equals("bing")) return "Bing";
        return "Google";
    }

    private void pickEngine() {
        final String[] names = {"Google", "DuckDuckGo", "Bing"};
        final String[] keys = {"google", "ddg", "bing"};
        int checked = 0;
        String e = prefs.getString("engine", "google");
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(e)) checked = i;

        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(Lang.t("সার্চ ইঞ্জিন"))
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    prefs.edit().putString("engine", keys[which]).apply();
                    d.dismiss();
                    refresh();
                })
                .show();
    }

    private String textZoomName() {
        int z = prefs.getInt("textzoom", 100);
        return z + "%";
    }

    private void pickZoom() {
        final String[] names = {Lang.t("ছোট (৮৫%)"), Lang.t("স্বাভাবিক (১০০%)"), Lang.t("বড় (১১৫%)"), Lang.t("আরও বড় (১৩০%)")};
        final int[] values = {85, 100, 115, 130};
        int z = prefs.getInt("textzoom", 100);
        int checked = 1;
        for (int i = 0; i < values.length; i++) if (values[i] == z) checked = i;

        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(Lang.t("লেখার আকার"))
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    prefs.edit().putInt("textzoom", values[which]).apply();
                    d.dismiss();
                    refresh();
                })
                .show();
    }

    // ---------------------------------------------------------------- থিম

    private static final String T_SYSTEM = "system", T_DARK = "dark", T_FORCE = "force";

    private String themeKey() {
        String t = prefs.getString("theme", null);
        if (t == null) {
            t = prefs.getBoolean("forcedark", false) ? T_FORCE
                    : (prefs.getBoolean("dark", false) ? T_DARK : T_SYSTEM);
            prefs.edit().putString("theme", t).apply();
        }
        return t;
    }

    private boolean themeIsDark() {
        String t = themeKey();
        if (T_FORCE.equals(t) || T_DARK.equals(t)) return true;
        return (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
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
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(Lang.t("থিম"))
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    prefs.edit().putString("theme", keys[which]).apply();
                    d.dismiss();
                    recreate();
                })
                .show();
    }

    private void refresh() {
        build();
    }

    // ---------------------------------------------------------------- data

    private void clearData(final String key, String label) {
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(Lang.deleteTitle(label))
                .setMessage(Lang.t("এটা ফিরিয়ে আনা যাবে না।"))
                .setPositiveButton(Lang.t("মুছুন"), (d, w) -> {
                    prefs.edit().putString(key, "[]").apply();
                    toast(Lang.deleted(label));
                    refresh();
                })
                .setNegativeButton(Lang.t("না"), null)
                .show();
    }

    private void clearCookies() {
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(Lang.t("কুকি ও ক্যাশে মুছবেন?"))
                .setMessage(Lang.t("সব সাইট থেকে লগআউট হয়ে যাবে।"))
                .setPositiveButton(Lang.t("মুছুন"), (d, w) -> {
                    android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
                    cm.removeAllCookies(null);
                    cm.flush();
                    android.webkit.WebStorage.getInstance().deleteAllData();
                    toast(Lang.t("কুকি ও ক্যাশে মোছা হয়েছে"));
                })
                .setNegativeButton(Lang.t("না"), null)
                .show();
    }

    private void clearAll() {
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(Lang.t("সব ডেটা মুছবেন?"))
                .setMessage(Lang.t("ইতিহাস, বুকমার্ক, কুকি আর ক্যাশে সব মুছে যাবে। সেটিংস আগের মতো থাকবে।"))
                .setPositiveButton(Lang.t("সব মুছুন"), (d, w) -> {
                    prefs.edit().putString("history", "[]").putString("bookmarks", "[]").apply();
                    android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
                    cm.removeAllCookies(null);
                    cm.flush();
                    android.webkit.WebStorage.getInstance().deleteAllData();
                    toast(Lang.t("সব ডেটা মোছা হয়েছে"));
                    refresh();
                })
                .setNegativeButton(Lang.t("না"), null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == REQ_ROLE) {
            toast(BrowserRole.isDefault(this)
                    ? Lang.t("ডিফল্ট ব্রাউজার সেট হয়েছে") : Lang.t("ডিফল্ট ব্রাউজার সেট হয়নি"));
            refresh();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
