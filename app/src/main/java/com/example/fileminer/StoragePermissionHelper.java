package com.example.fileminer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * ✅ StoragePermissionHelper
 * Handles storage permission checks for Android 6 → Android 14.
 *
 * Note:
 * - Android 11+ => MANAGE_EXTERNAL_STORAGE (All Files Access)
 * - Android 13+ => READ_MEDIA_* (if you want only media)
 * - Android 12 and below => READ_EXTERNAL_STORAGE
 */
public class StoragePermissionHelper {

    public static final int REQ_STORAGE = 501;

    public static boolean hasRequiredPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }

        // Android 12 and below
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openAllFilesAccessSettings(activity);
            return;
        }

        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                REQ_STORAGE
        );
    }

    public static void openAllFilesAccessSettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            activity.startActivity(intent);
        }
    }
}
