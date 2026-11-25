package io.github.mbanucu.blackviewautostart.sample;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.github.mbanucu.blackviewautostart.BlackviewAutostart;
import io.github.mbanucu.blackviewautostart.BootStart;

/**
 * The manual acceptance test from blackview-autostart#1: with the service
 * enabled and a DokeOS phone, toggling a label must move its switch through off
 * and back on. Without the vendor allowlist, the boot record is the other thing
 * this screen shows.
 */
public final class MainActivity extends Activity {

    private EditText label;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setTextSize(15);
        root.addView(status);

        label = new EditText(this);
        label.setInputType(InputType.TYPE_CLASS_TEXT);
        label.setText("Phone link proxy");
        label.setHint("exact app label in System Manager");
        root.addView(label);

        root.addView(button("Enable accessibility service", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BlackviewAutostart.openAccessibilitySettings(MainActivity.this);
            }
        }));
        root.addView(button("Open autostart panel", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!BlackviewAutostart.openPanel(MainActivity.this)) {
                    toast("System Manager has no autostart panel");
                }
            }
        }));
        root.addView(button("Toggle off and back on", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                request(true);
            }
        }));
        root.addView(button("Leave allowed", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                request(false);
            }
        }));

        setContentView(root);
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(listener);
        return button;
    }

    private void request(boolean toggle) {
        String target = label.getText().toString().trim();
        if (!BlackviewAutostart.isSupported(this)) {
            toast("This is not a DokeOS build");
            return;
        }
        if (!BlackviewAutostart.isAccessibilityEnabled(this)) {
            toast("Enable the accessibility service first");
            return;
        }
        boolean queued = toggle
                ? BlackviewAutostart.requestToggle(this, target)
                : BlackviewAutostart.requestAllowed(this, target, true);
        toast(queued ? "request sent: " + target : "request refused");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status == null) {
            return;
        }
        BootStart.Record boot = BootStart.lastBoot(this);
        status.setText("System Manager: "
                + (BlackviewAutostart.isSupported(this) ? "found" : "missing")
                + "\nAccessibility service: "
                + (BlackviewAutostart.isAccessibilityEnabled(this) ? "enabled" : "off")
                + "\n" + boot);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
