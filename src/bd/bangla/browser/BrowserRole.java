package bd.bangla.browser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;

/**
 * Small helper around Android's "default browser" role (API 29+) with a
 * fallback to the app's system settings page on older releases.
 */
final class BrowserRole {

    private BrowserRole() { }

    static boolean isDefault(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager rm =
                        (android.app.role.RoleManager) c.getSystemService(Context.ROLE_SERVICE);
                return rm != null
                        && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)
                        && rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER);
            }
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
            ResolveInfo ri = c.getPackageManager().resolveActivity(
                    i, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null
                    && c.getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    /** Shows the system prompt. Returns false when the role screen is unavailable. */
    static boolean request(Activity a, int code) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager rm =
                        (android.app.role.RoleManager) a.getSystemService(Context.ROLE_SERVICE);
                if (rm != null
                        && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                    a.startActivityForResult(rm.createRequestRoleIntent(
                            android.app.role.RoleManager.ROLE_BROWSER), code);
                    return true;
                }
            }
            a.startActivity(new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.getPackageName())));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
