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
    private int cBg, cSurface, cText, cMuted, cAccent, cLine;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        darkMode = prefs.getBoolean("dark", false);
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

    private void ripple(View v) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        if (tv.resourceId != 0) v.setBackgroundResource(tv.resourceId);
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

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(cText);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(12), dp(2), dp(12), dp(2));
        ripple(back);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        top.addView(back);

        ImageView logo = new ImageView(this);
        try {
            logo.setImageDrawable(getPackageManager().getApplicationIcon(getApplicationInfo()));
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(26), dp(26));
        llp.leftMargin = dp(2);
        llp.rightMargin = dp(10);
        top.addView(logo, llp);

        TextView title = new TextView(this);
        title.setText("সেটিংস");
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

        list.addView(section("সাধারণ"));
        list.addView(row("🔍", "সার্চ ইঞ্জিন", engineName(), new Runnable() {
            public void run() { pickEngine(); }
        }));
        list.addView(row("Aa", "লেখার আকার", textZoomName(), new Runnable() {
            public void run() { pickZoom(); }
        }));

        list.addView(section("চেহারা"));
        list.addView(switchRow("☾", "রাতের মোড", prefs.getBoolean("dark", false), "dark"));
        list.addView(switchRow("🖥", "ডেস্কটপ সাইট", prefs.getBoolean("desktop", false), "desktop"));

        list.addView(section("ব্রাউজিং"));
        list.addView(switchRow("অ", "বাংলায় অনুবাদ", prefs.getBoolean("autotranslate", true), "autotranslate"));
        list.addView(switchRow("⚙", "জাভাস্ক্রিপ্ট", prefs.getBoolean("js", true), "js"));
        list.addView(switchRow("🖼", "ছবি লোড", prefs.getBoolean("images", true), "images"));
        list.addView(switchRow("🗂", "লিংক নতুন ট্যাবে", prefs.getBoolean("newtab", false), "newtab"));

        list.addView(section("ডেটা"));
        list.addView(row("🕘", "ইতিহাস মুছুন", null, new Runnable() {
            public void run() { clearData("history", "ইতিহাস"); }
        }));
        list.addView(row("★", "বুকমার্ক মুছুন", null, new Runnable() {
            public void run() { clearData("bookmarks", "বুকমার্ক"); }
        }));
        list.addView(row("🧹", "কুকি ও ক্যাশে মুছুন", null, new Runnable() {
            public void run() { clearCookies(); }
        }));
        list.addView(row("⚠️", "সব ডেটা মুছুন", null, new Runnable() {
            public void run() { clearAll(); }
        }));

        list.addView(section("অ্যাপ"));
        list.addView(row("🌐", "ডিফল্ট ব্রাউজার",
                BrowserRole.isDefault(this) ? "সেট আছে" : "সেট করুন", new Runnable() {
                    public void run() {
                        if (!BrowserRole.request(SettingsActivity.this, REQ_ROLE)) {
                            toast("এই ফোনে সেটিংস খোলা যায়নি");
                        }
                    }
                }));
        list.addView(row("⬇️", "ডাউনলোড দেখুন", null, new Runnable() {
            public void run() {
                try {
                    startActivity(new android.content.Intent(
                            android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));
                } catch (Exception e) {
                    toast("ডাউনলোড অ্যাপ পাওয়া যায়নি");
                }
            }
        }));
        list.addView(row("ℹ", "সংস্করণ", "১.২", null));
        list.addView(row("🔗", "সোর্স কোড", "GitHub", new Runnable() {
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

        TextView ico = new TextView(this);
        ico.setText(icon);
        ico.setTextSize(15);
        ico.setGravity(Gravity.CENTER);
        ico.setTextColor(cText);
        box.addView(ico, new LinearLayout.LayoutParams(dp(30),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(15);
        name.setTextColor(cText);
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
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), dp(9), dp(14), dp(9));
        rowBg(box, true);

        TextView ico = new TextView(this);
        ico.setText(icon);
        ico.setTextSize(15);
        ico.setGravity(Gravity.CENTER);
        ico.setTextColor(cText);
        box.addView(ico, new LinearLayout.LayoutParams(dp(30),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(15);
        name.setTextColor(cText);
        box.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Switch sw = new Switch(this);
        sw.setChecked(on);
        if (Build.VERSION.SDK_INT >= 21) {
            sw.setThumbTintList(android.content.res.ColorStateList.valueOf(cAccent));
        }
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
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

        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("সার্চ ইঞ্জিন")
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
        final String[] names = {"ছোট (৮৫%)", "স্বাভাবিক (১০০%)", "বড় (১১৫%)", "আরও বড় (১৩০%)"};
        final int[] values = {85, 100, 115, 130};
        int z = prefs.getInt("textzoom", 100);
        int checked = 1;
        for (int i = 0; i < values.length; i++) if (values[i] == z) checked = i;

        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("লেখার আকার")
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    prefs.edit().putInt("textzoom", values[which]).apply();
                    d.dismiss();
                    refresh();
                })
                .show();
    }

    private void refresh() {
        build();
    }

    // ---------------------------------------------------------------- data

    private void clearData(final String key, String label) {
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle(label + " মুছবেন?")
                .setMessage("এটা ফিরিয়ে আনা যাবে না।")
                .setPositiveButton("মুছুন", (d, w) -> {
                    prefs.edit().putString(key, "[]").apply();
                    toast(label + " মোছা হয়েছে");
                    refresh();
                })
                .setNegativeButton("না", null)
                .show();
    }

    private void clearCookies() {
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("কুকি ও ক্যাশে মুছবেন?")
                .setMessage("সব সাইট থেকে লগআউট হয়ে যাবে।")
                .setPositiveButton("মুছুন", (d, w) -> {
                    android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
                    cm.removeAllCookies(null);
                    cm.flush();
                    android.webkit.WebStorage.getInstance().deleteAllData();
                    toast("কুকি ও ক্যাশে মোছা হয়েছে");
                })
                .setNegativeButton("না", null)
                .show();
    }

    private void clearAll() {
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("সব ডেটা মুছবেন?")
                .setMessage("ইতিহাস, বুকমার্ক, কুকি আর ক্যাশে সব মুছে যাবে। সেটিংস আগের মতো থাকবে।")
                .setPositiveButton("সব মুছুন", (d, w) -> {
                    prefs.edit().putString("history", "[]").putString("bookmarks", "[]").apply();
                    android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
                    cm.removeAllCookies(null);
                    cm.flush();
                    android.webkit.WebStorage.getInstance().deleteAllData();
                    toast("সব ডেটা মোছা হয়েছে");
                    refresh();
                })
                .setNegativeButton("না", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == REQ_ROLE) {
            toast(BrowserRole.isDefault(this)
                    ? "ডিফল্ট ব্রাউজার সেট হয়েছে" : "ডিফল্ট ব্রাউজার সেট হয়নি");
            refresh();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
