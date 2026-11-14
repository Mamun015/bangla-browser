package bd.bangla.browser;

import android.content.res.ColorStateList;
import android.os.Build;
import android.widget.Switch;

/** সুইচ jelas দেখা যায়: চালু থাকলে সবুজ ভরা, বন্ধ থাকলে ধূসর। */
final class SwitchStyle {

    private SwitchStyle() { }

    private static final int[][] STATES = new int[][]{
            {android.R.attr.state_checked},
            {-android.R.attr.state_checked}
    };

    static void apply(Switch sw, int accent, boolean dark) {
        if (Build.VERSION.SDK_INT < 21) return;
        sw.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        sw.setTrackTintList(new ColorStateList(STATES, new int[]{
                accent,
                dark ? 0xFF3A4641 : 0xFFC4D0CA
        }));
    }
}
