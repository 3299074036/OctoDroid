package com.gh4a.worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.activities.ReleaseInfoActivity;
import com.gh4a.activities.home.HomeActivity;
import com.gh4a.fragment.RadarCache;
import com.gh4a.fragment.RadarGraphQL;
import com.gh4a.fragment.ReleaseRadarFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 后台定时检查 Star 仓库的新 Release：复用 ReleaseRadarFragment 的单次
 * GraphQL 查询，对比上次记录的 tag，有新版本就发系统通知。
 *
 * 首次运行只建立基线不打扰，避免一次性刷屏。
 */
public class ReleaseRadarWorker extends Worker {
    private static final String TAG = "ReleaseRadarWorker";

    private static final String CHANNEL_RELEASE_RADAR = "channel_release_radar";
    private static final String GROUP_ID_RADAR = "release_radar";
    private static final String WORK_TAG = "job_release_radar";

    private static final String PREFS = "release_radar_notify";
    private static final String KEY_KNOWN_TAGS = "known_tags_"; // + login

    public static void schedule(Context context, int intervalMinutes) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(ReleaseRadarWorker.class,
                        intervalMinutes, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .addTag(WORK_TAG)
                        .build();
        Log.d(TAG, "Scheduling release radar check every " + intervalMinutes + " min");
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG);
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_TAG,
                ExistingPeriodicWorkPolicy.REPLACE, request);
    }

    public static void cancel(Context context) {
        Log.d(TAG, "Canceling release radar check");
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG);
        WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG);
    }

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_RELEASE_RADAR,
                context.getString(R.string.channel_release_radar_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.channel_release_radar_description));
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(channel);
    }

    public ReleaseRadarWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        final Context context = getApplicationContext();
        final String login = Gh4Application.get().getAuthLogin();
        if (login == null) {
            return Result.success();
        }

        final List<ReleaseRadarFragment.RadarItem> items;
        try {
            items = RadarGraphQL.fetch(login).blockingGet();
        } catch (Exception e) {
            Log.d(TAG, "Radar fetch failed", e);
            return Result.retry();
        }

        SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Map<String, String> known = loadKnownTags(prefs, login);
        boolean firstRun = known.isEmpty();

        Map<String, String> fresh = new HashMap<>();
        List<ReleaseRadarFragment.RadarItem> newReleases = new ArrayList<>();
        for (ReleaseRadarFragment.RadarItem item : items) {
            String key = item.owner + "/" + item.repo;
            fresh.put(key, item.tagName);
            String oldTag = known.get(key);
            if (!firstRun && oldTag != null && !oldTag.equals(item.tagName)) {
                newReleases.add(item);
            }
        }
        saveKnownTags(prefs, login, fresh);
        // 顺手刷新 Star 更新页的缓存，打开页面即最新
        RadarCache.save(context, login, items);

        if (firstRun || newReleases.isEmpty()) {
            Log.d(TAG, firstRun ? "Baseline established" : "No new releases");
            return Result.success();
        }

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        for (ReleaseRadarFragment.RadarItem item : newReleases) {
            nm.notify(itemKey(item).hashCode(), buildRepoNotification(context, item));
        }
        nm.notify(0, buildSummaryNotification(context, newReleases));
        Log.d(TAG, "Notified " + newReleases.size() + " new releases");
        return Result.success();
    }

    private static String itemKey(ReleaseRadarFragment.RadarItem item) {
        return item.owner + "/" + item.repo;
    }

    private android.app.Notification buildRepoNotification(Context context,
            ReleaseRadarFragment.RadarItem item) {
        String repoName = itemKey(item);
        String title = context.getString(R.string.release_radar_notify_title, repoName);
        String text = context.getString(R.string.release_radar_notify_text, item.tagName);

        Intent intent = ReleaseInfoActivity.makeIntent(
                context, item.owner, item.repo, item.releaseId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context,
                itemKey(item).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return makeBaseBuilder(context)
                .setGroup(GROUP_ID_RADAR)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();
    }

    private android.app.Notification buildSummaryNotification(Context context,
            List<ReleaseRadarFragment.RadarItem> newReleases) {
        String title = context.getString(R.string.release_radar_notify_summary_title);
        String text = context.getResources().getQuantityString(
                R.plurals.release_radar_notify_summary_text,
                newReleases.size(), newReleases.size());

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                HomeActivity.makeIntent(context, R.id.release_radar)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.InboxStyle inbox =
                new NotificationCompat.InboxStyle()
                        .setBigContentTitle(text);
        for (ReleaseRadarFragment.RadarItem item : newReleases) {
            inbox.addLine(itemKey(item) + " " + item.tagName);
        }

        return makeBaseBuilder(context)
                .setGroup(GROUP_ID_RADAR)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setStyle(inbox)
                .build();
    }

    private NotificationCompat.Builder makeBaseBuilder(Context context) {
        return new NotificationCompat.Builder(context, CHANNEL_RELEASE_RADAR)
                .setSmallIcon(R.drawable.notification)
                .setColor(ContextCompat.getColor(context, R.color.octodroid));
    }

    private static Map<String, String> loadKnownTags(SharedPreferences prefs, String login) {
        Map<String, String> map = new HashMap<>();
        String raw = prefs.getString(KEY_KNOWN_TAGS + login, null);
        if (raw == null) {
            return map;
        }
        for (String entry : raw.split(";")) {
            int eq = entry.indexOf('=');
            if (eq > 0) {
                map.put(entry.substring(0, eq), entry.substring(eq + 1));
            }
        }
        return map;
    }

    private static void saveKnownTags(SharedPreferences prefs, String login,
            Map<String, String> tags) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : tags.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        prefs.edit().putString(KEY_KNOWN_TAGS + login, sb.toString()).apply();
    }
}
