package dev.min.launcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Background-maintained, immutable snapshot of every launchable activity. */
public final class AppIndex {
    public interface Listener {
        void onAppsChanged(List<AppEntry> apps);
    }

    private final Context context;
    private final PackageManager packageManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private volatile List<AppEntry> apps = Collections.emptyList();
    private boolean receiverRegistered;
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context receiverContext, Intent intent) {
            reload();
        }
    };

    public AppIndex(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = context.getPackageManager();
    }

    public void start() {
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addDataScheme("package");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(packageReceiver, filter);
            }
            receiverRegistered = true;
        }
        reload();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
        if (!apps.isEmpty()) {
            listener.onAppsChanged(apps);
        }
    }

    public List<AppEntry> snapshot() {
        return apps;
    }

    public void reload() {
        executor.execute(() -> {
            Intent launchIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = packageManager.queryIntentActivities(launchIntent,
                    PackageManager.MATCH_ALL);
            ArrayList<AppEntry> loaded = new ArrayList<>(resolved.size());
            Set<String> components = new HashSet<>();
            for (ResolveInfo info : resolved) {
                if (info.activityInfo == null) {
                    continue;
                }
                ComponentName component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                if (!components.add(component.flattenToString())) {
                    continue;
                }
                CharSequence loadedLabel = info.loadLabel(packageManager);
                String label = loadedLabel == null ? info.activityInfo.packageName : loadedLabel.toString().trim();
                if (label.isEmpty()) {
                    label = info.activityInfo.packageName;
                }
                loaded.add(new AppEntry(label, component));
            }
            Collections.sort(loaded, (left, right) -> left.normalizedLabel.compareTo(right.normalizedLabel));
            List<AppEntry> immutable = Collections.unmodifiableList(loaded);
            mainHandler.post(() -> {
                apps = immutable;
                for (Listener listener : new ArrayList<>(listeners)) {
                    listener.onAppsChanged(immutable);
                }
            });
        });
    }

    public void shutdown() {
        if (receiverRegistered) {
            context.unregisterReceiver(packageReceiver);
            receiverRegistered = false;
        }
        executor.shutdownNow();
    }
}
