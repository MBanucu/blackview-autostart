package io.github.mbanucu.blackviewautostart;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives {@link BlackviewAutostart#ACTION_TOGGLE} from the app that owns this
 * process. The component is explicit and not exported, so another app cannot
 * drive the screen walk.
 */
public final class AutostartCommand extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !BlackviewAutostart.ACTION_TOGGLE.equals(intent.getAction())) {
            return;
        }
        String label = intent.getStringExtra(BlackviewAutostart.EXTRA_LABEL);
        int mode = intent.getIntExtra(BlackviewAutostart.EXTRA_MODE,
                BlackviewAutostart.MODE_TOGGLE);
        AutostartService.request(label, mode);
    }
}
