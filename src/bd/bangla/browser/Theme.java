package bd.bangla.browser;

import android.graphics.Color;

/** অ্যাপের রং এক জায়গায়: দুই থিমের পালিশ করা রং এখানেই। */
public class Theme {

    public final boolean dark;
    public final int bg, surface, surface2, card, text, muted, accent, accentSoft, line;

    public Theme(boolean dark) {
        this.dark = dark;
        if (dark) {
            bg = Color.parseColor("#080B0A");
            surface = Color.parseColor("#121715");
            surface2 = Color.parseColor("#1A211E");
            card = Color.parseColor("#131917");
            text = Color.parseColor("#EAF1EE");
            muted = Color.parseColor("#8D9A95");
            accent = Color.parseColor("#34D399");
            accentSoft = Color.parseColor("#16332A");
            line = Color.parseColor("#1F2724");
        } else {
            bg = Color.parseColor("#FFFFFF");
            surface = Color.parseColor("#F3F7F5");
            surface2 = Color.parseColor("#E9F0EC");
            card = Color.parseColor("#FFFFFF");
            text = Color.parseColor("#0D1412");
            muted = Color.parseColor("#667471");
            accent = Color.parseColor("#007A5A");
            accentSoft = Color.parseColor("#E3F4ED");
            line = Color.parseColor("#E4EBE7");
        }
    }
}
