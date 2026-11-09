package dev.min.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Exported, synchronous ADB command endpoint. */
public final class CliReceiver extends BroadcastReceiver {
    public static final String ACTION_COMMAND = "dev.min.launcher.COMMAND";
    public static final String EXTRA_COMMAND = "cmd";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_COMMAND.equals(intent.getAction())) {
            setResultData("error: unsupported action");
            return;
        }
        if (!(context.getApplicationContext() instanceof LauncherApplication)) {
            setResultData("error: launcher application is unavailable");
            return;
        }
        LauncherApplication application = (LauncherApplication) context.getApplicationContext();
        String result;
        try {
            result = application.executeCommand(intent.getStringExtra(EXTRA_COMMAND));
        } catch (RuntimeException failure) {
            result = "error: command failed safely";
        }
        setResultData(result);
    }
}
