package io.github.mbanucu.blackviewautostart;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * Records whether the app's boot receiver actually ran, and how long after boot.
 *
 * <p>The allowlist is only half of the DokeOS puzzle: without it the package is
 * {@code stopped=true} at boot and the vendor's {@code AppAutoStartupPolicy}
 * blocks the receiver. With it, {@code LOCKED_BOOT_COMPLETED} arrives at about
 * 27 s. This record is what makes that visible instead of guessed.
 *
 * <p>The write goes to device-protected storage, because the boot broadcast can
 * arrive before the first unlock and credential-encrypted storage is not
 * readable there.
 */
public final class BootStart {

    private static final String PREFS = "blackview-boot";
    private static final String KEY_ELAPSED = "elapsed_ms";
    private static final String KEY_WALL = "wall_ms";
    private static final String KEY_BEFORE_UNLOCK = "before_unlock";
    private static final String KEY_SIGNALS = "signals";

    /** What the app knows about its last boot. */
    public static final class Record {
        /** Whether any boot broadcast has been recorded. */
        public final boolean seen;
        /** {@link SystemClock#elapsedRealtime()} when the first signal arrived. */
        public final long delayMs;
        /** Wall-clock time of that signal. */
        public final long wallMs;
        /** Whether a signal arrived before the first unlock. */
        public final boolean beforeUnlock;
        /** How many boot signals were recorded in that boot. */
        public final int signals;

        Record(boolean seen, long delayMs, long wallMs, boolean beforeUnlock, int signals) {
            this.seen = seen;
            this.delayMs = delayMs;
            this.wallMs = wallMs;
            this.beforeUnlock = beforeUnlock;
            this.signals = signals;
        }

        @Override
        public String toString() {
            if (!seen) {
                return "no boot broadcast recorded";
            }
            return "boot signal at " + delayMs + " ms"
                    + (beforeUnlock ? " (before unlock)" : "")
                    + ", signals=" + signals;
        }
    }

    private BootStart() {
    }

    /** The last recorded boot, or a record with {@code seen == false}. */
    public static Record lastBoot(Context context) {
        SharedPreferences prefs = prefs(context);
        long elapsed = prefs.getLong(KEY_ELAPSED, -1);
        if (elapsed < 0) {
            return new Record(false, 0, 0, false, 0);
        }
        return new Record(true, elapsed, prefs.getLong(KEY_WALL, 0),
                prefs.getBoolean(KEY_BEFORE_UNLOCK, false), prefs.getInt(KEY_SIGNALS, 1));
    }

    /** Forgets the record, so the next boot is the first one observed. */
    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        Context storage = context.isDeviceProtectedStorage()
                ? context
                : context.createDeviceProtectedStorageContext();
        return storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * The manifest-declared receiver. Keeping it here means a consumer gets the
     * boot record from the AAR, with no receiver class to write.
     */
    public static final class Recorder extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            boolean locked = Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action);
            if (!locked && !Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                return;
            }
            long elapsed = SystemClock.elapsedRealtime();
            long wall = System.currentTimeMillis();
            SharedPreferences prefs = prefs(context);
            long previousWall = prefs.getLong(KEY_WALL, 0);
            long previousElapsed = prefs.getLong(KEY_ELAPSED, -1);

            // The two broadcasts arrive minutes apart in the same boot. Keep the
            // earliest delay and OR the before-unlock fact, but treat a wall gap
            // wider than ten minutes as a new boot.
            boolean sameBoot = previousElapsed >= 0 && wall - previousWall < 10 * 60_000L;
            SharedPreferences.Editor edit = prefs.edit();
            if (!sameBoot) {
                edit.putLong(KEY_ELAPSED, elapsed)
                        .putLong(KEY_WALL, wall)
                        .putBoolean(KEY_BEFORE_UNLOCK, locked)
                        .putInt(KEY_SIGNALS, 1);
            } else {
                edit.putLong(KEY_ELAPSED, Math.min(previousElapsed, elapsed))
                        .putBoolean(KEY_BEFORE_UNLOCK,
                                prefs.getBoolean(KEY_BEFORE_UNLOCK, false) || locked)
                        .putInt(KEY_SIGNALS, prefs.getInt(KEY_SIGNALS, 1) + 1);
            }
            edit.apply();
        }
    }
}
