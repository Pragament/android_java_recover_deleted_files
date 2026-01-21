package com.example.fileminer;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.fileminer.databinding.ActivityPermissionBinding;

// Visual screen to guide user in granting "All Files Access"
public class PermissionActivity extends Activity {

    private ActivityPermissionBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        super.onCreate(savedInstanceState);

        // Inflate layout using ViewBinding
        binding = ActivityPermissionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setImageForMode();
        setStyledText();

        // ✅ NEW: If permission already granted, directly open app
        if (PermissionUtils.checkStoragePermission(this)) {
            openMainActivity();
            return;
        }

        // ✅ UPDATED: Request permission properly based on Android version
        binding.allowButton.setOnClickListener(v -> {
            // Android 11+ => open All Files Access settings
            // Android 10 and below => request runtime permissions
            PermissionUtils.requestStoragePermission(PermissionActivity.this);
        });
    }

    private void setImageForMode() {
        int currentNightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            binding.imageView.setImageResource(R.drawable.filetwo); // Dark mode image
        } else {
            binding.imageView.setImageResource(R.drawable.filetwo); // Light mode image
        }
    }

    private void setStyledText() {
        String text = "\"All Files Access\" permission is required to search for files.";
        SpannableString spannable = new SpannableString(text);
        spannable.setSpan(new ForegroundColorSpan(Color.RED), 1, 17, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.permissionText.setText(spannable);
    }

    /**
     * ⚠️ OLD METHOD kept (not removed).
     * But now we use PermissionUtils.requestStoragePermission() for better handling.
     */
    private void openPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                } catch (Exception ex) {
                    Toast.makeText(this, "Unable to open settings", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ✅ NEW: After coming back from settings/permission popup, check permission again
        if (PermissionUtils.checkStoragePermission(this)) {
            openMainActivity();
        } else {
            // Optional: show message only for Android 11+ when user comes back without granting
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "Permission not granted yet", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void openMainActivity() {
        Intent intent = new Intent(PermissionActivity.this, MainActivity2.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Please grant permission to proceed.", Toast.LENGTH_SHORT).show();
    }
}
