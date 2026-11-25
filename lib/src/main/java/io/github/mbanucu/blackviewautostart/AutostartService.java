package io.github.mbanucu.blackviewautostart;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives DokeOS System Manager's autostart list.
 *
 * <p>The vendor draws the list as a plain label beside a checkable Switch with
 * nothing tying the two nodes together, so a text search does not find a row.
 * Rows are paired by vertical overlap instead. The list also keeps its scroll
 * position between opens, so a scan always rewinds to the top first.
 *
 * <p>DokeOS wants an off/on pair when a row is re-granted; a reinstall silently
 * invalidates the entry while the row still reads on. That is
 * {@link BlackviewAutostart#MODE_TOGGLE}, and it is the default request.
 */
public final class AutostartService extends AccessibilityService {

    private static final String TAG = "BlackviewAutostart";
    /** The list can be long; the original walk allowed 40 screens. */
    private static final int MAX_PAGES = 40;
    /** Rows keep their order, so one settled page is worth reading. */
    private static final long PAGE_SETTLE = 450;

    private static volatile AutostartService instance;
    private static volatile Request pending;
    private int page = -1;

    private static final class Request {
        final String label;
        final int mode;

        Request(String label, int mode) {
            this.label = label;
            this.mode = mode;
        }
    }

    /** A row's label and the switch drawn beside it. */
    private static final class Row {
        final String label;
        final AccessibilityNodeInfo box;

        Row(String label, AccessibilityNodeInfo box) {
            this.label = label;
            this.box = box;
        }
    }

    /**
     * Queues a request. A broadcast can arrive before the service is connected,
     * so the latest request waits for {@link #onServiceConnected()}.
     */
    static void request(String label, int mode) {
        if (label == null || label.trim().isEmpty()) {
            return;
        }
        Request next = new Request(label.trim(), mode);
        AutostartService running = instance;
        if (running == null) {
            pending = next;
            Log.i(TAG, "service not connected; queued " + next.label);
            return;
        }
        running.run(next);
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        Log.i(TAG, "service connected");
        Request queued = pending;
        if (queued != null) {
            pending = null;
            run(queued);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Polling, not event driven: the vendor list redraws without telling us.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    private void run(final Request request) {
        final long begin = SystemClock.elapsedRealtime();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    flow(request);
                } catch (Throwable failed) {
                    Log.i(TAG, request.label + " failed="
                            + failed.getClass().getSimpleName() + ": " + failed.getMessage());
                }
                Log.i(TAG, request.label + " finished in "
                        + (SystemClock.elapsedRealtime() - begin) + " ms");
            }
        }, "blackview-autostart").start();
    }

    private void flow(Request request) throws InterruptedException {
        if (!openList()) {
            Log.i(TAG, request.label + " failed=autostart list not reachable");
            return;
        }
        Row row = findRow(request.label);
        if (row == null) {
            Log.i(TAG, request.label + "=not found");
            return;
        }
        row = nudgeIntoView(request.label, row);
        if (row.box == null || !row.box.isCheckable()) {
            Log.i(TAG, request.label + "=no switch");
            return;
        }
        switch (request.mode) {
            case BlackviewAutostart.MODE_ON:
                setAllowed(row, true);
                break;
            case BlackviewAutostart.MODE_OFF:
                setAllowed(row, false);
                break;
            default:
                retoggle(row);
                break;
        }
    }

    /**
     * Opens the exported autostart page, and falls back to the launcher path if
     * this ROM build does not export it.
     */
    private boolean openList() throws InterruptedException {
        start(new ComponentName(BlackviewAutostart.MANAGER_PACKAGE,
                "com.freeme.freemanager.autoboot.AutoStartMainActivity"));
        sleep(1800);
        page = -1;
        if (find("Auto Start Management", 3000) != null) {
            return true;
        }

        start(ComponentName.unflattenFromString(BlackviewAutostart.MAIN_ACTIVITY));
        sleep(1500);
        AccessibilityNodeInfo entry = find("App Management", 5000);
        if (entry == null) {
            return false;
        }
        click(entry);
        AccessibilityNodeInfo autostart = find("Auto Start Management", 6000);
        if (autostart == null) {
            return false;
        }
        click(autostart);
        sleep(1500);
        page = -1;
        return true;
    }

    private void start(ComponentName component) {
        if (component == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (RuntimeException missing) {
            Log.i(TAG, "cannot start " + component.flattenToShortString() + ": "
                    + missing.getMessage());
        }
    }

    private void retoggle(Row row) throws InterruptedException {
        String label = row.label;
        boolean was = row.box.isChecked();
        tap(row.box);
        Row after = waitChecked(label, !was, 3000);
        if (after == null) {
            Log.i(TAG, label + "=tap ignored");
            return;
        }
        Log.i(TAG, label + " was=" + (was ? "on" : "off"));
        if (was) {
            // It was on, so the first tap removed the allowance. The pair is
            // what makes the vendor write the entry again, and it must end on.
            tap(after.box);
            Row end = waitChecked(label, true, 3000);
            Log.i(TAG, label + " end=" + (end != null ? "on" : "off"));
        } else {
            Log.i(TAG, label + " end=on");
        }
    }

    private void setAllowed(Row row, boolean allowed) throws InterruptedException {
        String label = row.label;
        if (row.box.isChecked() == allowed) {
            Log.i(TAG, label + " already " + (allowed ? "on" : "off"));
            return;
        }
        tap(row.box);
        Row end = waitChecked(label, allowed, 3000);
        Log.i(TAG, label + " end=" + (end != null ? (allowed ? "on" : "off") : "unchanged"));
    }

    // --- accessibility helpers -------------------------------------------------

    private List<AccessibilityNodeInfo> nodes() {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return out;
        }
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            out.add(node);
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.add(child);
                }
            }
        }
        return out;
    }

    private List<Row> visibleRows() {
        List<AccessibilityNodeInfo> all = nodes();
        List<Row> out = new ArrayList<>();
        for (AccessibilityNodeInfo box : all) {
            if (!box.isCheckable()) {
                continue;
            }
            Rect span = new Rect();
            box.getBoundsInScreen(span);
            String best = null;
            int bestOverlap = 0;
            for (AccessibilityNodeInfo node : all) {
                if (node.isCheckable()) {
                    continue;
                }
                CharSequence text = node.getText();
                if (text == null) {
                    text = node.getContentDescription();
                }
                if (text == null || text.toString().trim().isEmpty()) {
                    continue;
                }
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                int overlap = Math.min(bounds.bottom, span.bottom)
                        - Math.max(bounds.top, span.top);
                if (overlap > bestOverlap) {
                    bestOverlap = overlap;
                    best = text.toString().trim();
                }
            }
            if (best != null) {
                out.add(new Row(best, box));
            }
        }
        return out;
    }

    private Row rowOnPage(String label) {
        String want = label.toLowerCase(java.util.Locale.ROOT);
        for (Row row : visibleRows()) {
            if (row.label.toLowerCase(java.util.Locale.ROOT).contains(want)) {
                return row;
            }
        }
        return null;
    }

    private Row findRow(String label) throws InterruptedException {
        Row hit = rowOnPage(label);
        if (hit != null) {
            return hit;
        }
        scrollToTop();
        hit = rowOnPage(label);
        for (int i = 0; i < MAX_PAGES && hit == null; i++) {
            if (!scrollForward()) {
                break;
            }
            sleep(PAGE_SETTLE);
            hit = rowOnPage(label);
        }
        return hit;
    }

    /** Keeps a switch that sits at the screen edge from failing the tap. */
    private Row nudgeIntoView(String label, Row row) throws InterruptedException {
        int height = getResources().getDisplayMetrics().heightPixels;
        for (int attempt = 0; attempt < 3; attempt++) {
            Rect span = new Rect();
            row.box.getBoundsInScreen(span);
            if (span.height() > 0 && span.top >= 0 && span.bottom <= height) {
                return row;
            }
            boolean lower = span.centerY() > height / 2;
            if (lower ? !scrollForward() : !scrollBackward()) {
                return row;
            }
            sleep(500);
            Row again = rowOnPage(label);
            if (again == null) {
                return row;
            }
            row = again;
        }
        return row;
    }

    private void scrollToTop() throws InterruptedException {
        for (int i = 0; i < MAX_PAGES; i++) {
            if (!scrollBackward()) {
                break;
            }
            sleep(250);
        }
        page = 0;
    }

    private boolean scrollForward() {
        AccessibilityNodeInfo scroller = firstScrollable(getRootInActiveWindow());
        boolean moved = scroller != null
                && scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        if (moved && page >= 0) {
            page++;
        }
        return moved;
    }

    private boolean scrollBackward() {
        AccessibilityNodeInfo scroller = firstScrollable(getRootInActiveWindow());
        boolean moved = scroller != null
                && scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        if (moved && page > 0) {
            page--;
        }
        return moved;
    }

    private AccessibilityNodeInfo firstScrollable(AccessibilityNodeInfo root) {
        if (root == null) {
            return null;
        }
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            if (node.isScrollable()) {
                return node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.add(child);
                }
            }
        }
        return null;
    }

    private AccessibilityNodeInfo find(String text, long timeoutMs) throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        do {
            for (AccessibilityNodeInfo node : nodes()) {
                CharSequence label = node.getText();
                if (label == null) {
                    label = node.getContentDescription();
                }
                if (label != null
                        && label.toString().toLowerCase(java.util.Locale.ROOT)
                        .contains(text.toLowerCase(java.util.Locale.ROOT))) {
                    return node;
                }
            }
            sleep(200);
        } while (SystemClock.elapsedRealtime() < deadline);
        return null;
    }

    private Row waitChecked(String label, boolean expected, long timeoutMs)
            throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        do {
            Row row = rowOnPage(label);
            if (row != null && row.box.isChecked() == expected) {
                return row;
            }
            sleep(150);
        } while (SystemClock.elapsedRealtime() < deadline);
        return null;
    }

    private boolean click(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        AccessibilityNodeInfo parent = node.getParent();
        return parent != null && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private void tap(AccessibilityNodeInfo node) {
        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            click(node.getParent());
        }
    }

    private static void sleep(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
