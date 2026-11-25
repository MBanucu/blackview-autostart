package io.github.mbanucu.blackviewautostart;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/**
 * The supported entry points for DokeOS's autostart allowlist.
 *
 * <p>The allowlist is vendor-gated: it is guarded by a signature permission
 * ({@code com.freeme.freemanager.permission.ACCESS_INTERFACE}) that neither adb
 * nor an ordinary app can hold, so nothing here writes it. The library can only
 * drive the System Manager screen — with the accessibility service, or the
 * caller can drive the same screen over adb with {@code cli/autostart-allow.sh}.
 *
 * <p>The accessibility service has to be enabled by the user. This class never
 * enables it; {@link #openAccessibilitySettings(Context)} takes the user to the
 * screen and stops there.
 */
public final class BlackviewAutostart {

    /** Package that owns the DokeOS System Manager. */
    public static final String MANAGER_PACKAGE = "com.blackview.systemmanager";

    /** The exported autostart page, when the ROM has one. */
    public static final String AUTOSTART_ACTIVITY =
            MANAGER_PACKAGE + "/com.freeme.freemanager.autoboot.AutoStartMainActivity";

    /** The launcher activity, used as the fallback path. */
    public static final String MAIN_ACTIVITY = MANAGER_PACKAGE + "/.MainActivity";

    /** Ordered broadcast sent by {@link #requestToggle(Context, String)}. */
    public static final String ACTION_TOGGLE =
            "io.github.mbanucu.blackviewautostart.TOGGLE";

    /** The row label to act on. */
    public static final String EXTRA_LABEL = "label";

    /** One of {@link #MODE_TOGGLE}, {@link #MODE_ON}, {@link #MODE_OFF}. */
    public static final String EXTRA_MODE = "mode";

    /** Off then on: what makes the vendor write the row again. */
    public static final int MODE_TOGGLE = 0;

    /** Leave the row allowed. */
    public static final int MODE_ON = 1;

    /** Leave the row not allowed. */
    public static final int MODE_OFF = 2;

    private BlackviewAutostart() {
    }

    /**
     * Whether this looks like a DokeOS build with the System Manager the screen
     * walk expects. A false result is a named reason to stop, not a fallback.
     */
    public static boolean isSupported(Context context) {
        try {
            context.getPackageManager().getPackageInfo(MANAGER_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException missing) {
            return false;
        }
    }

    /** Whether the user has enabled {@link AutostartService} in Settings. */
    public static boolean isAccessibilityEnabled(Context context) {
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }
        List<AccessibilityServiceInfo> enabled =
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        ComponentName expected = new ComponentName(context, AutostartService.class);
        for (AccessibilityServiceInfo info : enabled) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) {
                continue;
            }
            String pkg = info.getResolveInfo().serviceInfo.packageName;
            String cls = info.getResolveInfo().serviceInfo.name;
            if (expected.getPackageName().equals(pkg) && expected.getClassName().equals(cls)) {
                return true;
            }
        }
        return false;
    }

    /** Opens Android's accessibility settings so the user can enable the service. */
    public static void openAccessibilitySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Opens System Manager's autostart list without toggling anything.
     *
     * @return false when the vendor activity is missing and the launcher
     *     fallback also could not be opened.
     */
    public static boolean openPanel(Context context) {
        if (startVendor(context, AUTOSTART_ACTIVITY)) {
            return true;
        }
        return startVendor(context, MAIN_ACTIVITY);
    }

    /**
     * Asks the accessibility service to take the row through off and back on.
     * This is the DokeOS re-registration step, not a plain switch flip.
     *
     * @return false when the service is not enabled, the label is blank, or the
     *     phone is not a DokeOS build. The request is not queued in that case;
     *     a caller that wants to prompt for the service should check
     *     {@link #isAccessibilityEnabled(Context)} first.
     */
    public static boolean requestToggle(Context context, String label) {
        return request(context, label, MODE_TOGGLE);
    }

    /**
     * Asks the accessibility service to leave the row in the requested state.
     *
     * @param allowed true to leave it allowed, false to remove the allowance.
     */
    public static boolean requestAllowed(Context context, String label, boolean allowed) {
        return request(context, label, allowed ? MODE_ON : MODE_OFF);
    }

    private static boolean request(Context context, String label, int mode) {
        if (label == null || label.trim().isEmpty()) {
            return false;
        }
        if (!isSupported(context) || !isAccessibilityEnabled(context)) {
            return false;
        }
        Intent intent = new Intent(ACTION_TOGGLE);
        intent.setComponent(new ComponentName(context, AutostartCommand.class));
        intent.putExtra(EXTRA_LABEL, label.trim());
        intent.putExtra(EXTRA_MODE, mode);
        context.sendBroadcast(intent);
        return true;
    }

    private static boolean startVendor(Context context, String component) {
        ComponentName name = ComponentName.unflattenFromString(component);
        if (name == null) {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(name);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (context.getPackageManager().resolveActivity(intent, 0) == null) {
            return false;
        }
        context.startActivity(intent);
        return true;
    }
}
