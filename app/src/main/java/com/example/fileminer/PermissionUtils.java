package com.example.fileminer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;

public class PermissionUtils {

    public static final int REQUEST_STORAGE_PERMISSION = 100;

    /**
     * ✅ Checks if storage access permission is granted.
     * - Android 11+ => All Files Access
     * - Android 13+ => Media permissions
     * - Android 12 and below => READ_EXTERNAL_STORAGE
     */
    public static boolean checkStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (All files access)
            return Environment.isExternalStorageManager();
        } else {
            // Android 10 and below
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * ✅ Requests storage permissions based on Android version.
     * We DO NOT request POST_NOTIFICATIONS here (separate feature).
     */
    @SuppressLint("InlinedApi")
    public static void requestStoragePermission(Activity activity) {
        WeakReference<Activity> weakActivity = new WeakReference<>(activity);
        Activity safeActivity = weakActivity.get();

        if (safeActivity == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // ✅ Android 11+ special permission: All Files Access
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + safeActivity.getPackageName()));
                safeActivity.startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                safeActivity.startActivity(intent);
            }
            return;
        }

        // ✅ Android 6 to Android 10: runtime permission
        ActivityCompat.requestPermissions(
                safeActivity,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                REQUEST_STORAGE_PERMISSION
        );
    }

    /**
     * ✅ OPTIONAL: Notification permission request (Android 13+)
     * Keep separate from storage permission flow.
     */
    public static boolean hasNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    200
            );
        }
    }
}
