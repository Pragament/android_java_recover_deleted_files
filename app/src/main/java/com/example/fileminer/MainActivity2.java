package com.example.fileminer;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;

import com.example.fileminer.databinding.ActivityMain2Binding;

import java.util.Locale;

public class MainActivity2 extends AppCompatActivity {

    private ActivityMain2Binding binding;
    private StorageViewModel viewModel;

    private static final String[] FILE_TYPES = {"Photo", "Video", "Audio", "Document", "Deleted", "Hidden", "OtherFiles"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        super.onCreate(savedInstanceState);

        binding = ActivityMain2Binding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(StorageViewModel.class);

        restoreProgress(savedInstanceState);

        // ✅ NEW: Check permission properly (Android 6 → 14)
        if (PermissionUtils.checkStoragePermission(this)) {
            displayStorageInfo();
        } else {
            // Open PermissionActivity (your launcher permission screen)
            startActivity(new Intent(this, PermissionActivity.class));
            finish();
            return;
        }

        setupButtonListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ✅ NEW: Safety check when user returns from settings
        if (!PermissionUtils.checkStoragePermission(this)) {
            startActivity(new Intent(this, PermissionActivity.class));
            finish();
        }
    }

    private void restoreProgress(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            viewModel.lastProgress = savedInstanceState.getInt("saved_progress", 0);
        }
        binding.storageProgress.setProgress(viewModel.lastProgress);
        binding.progressText.setText(viewModel.lastProgress + "%");
    }

    private void setupButtonListeners() {
        binding.btnPhoto.setOnClickListener(v -> navigateToCategory(FILE_TYPES[0]));
        binding.btnVideo.setOnClickListener(v -> navigateToCategory(FILE_TYPES[1]));
        binding.btnAudio.setOnClickListener(v -> navigateToCategory(FILE_TYPES[2]));
        binding.btnDocument.setOnClickListener(v -> navigateToCategory(FILE_TYPES[3]));
        binding.btnRecycle.setOnClickListener(v -> navigateToCategory(FILE_TYPES[4]));
        binding.btnHidden.setOnClickListener(v -> navigateToCategory(FILE_TYPES[5]));
        binding.btnOtherFiles.setOnClickListener(v -> navigateToCategory(FILE_TYPES[6]));
    }

    private void navigateToCategory(String category) {
        Intent intent = new Intent(MainActivity2.this, RestoredFilesActivity.class);
        intent.putExtra("fileType", category);
        startActivity(intent);
    }

    private void displayStorageInfo() {
        // ⚠️ NOTE: Environment.getExternalStorageDirectory() is deprecated but kept for your existing logic.
        // We only ensure permission is granted before calling it.
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long totalBytes = stat.getTotalBytes();
        long freeBytes = stat.getAvailableBytes();
        long usedBytes = totalBytes - freeBytes;

        int usedPercent = (int) ((usedBytes * 100) / totalBytes);

        binding.usedStorage.setText(String.format(Locale.getDefault(), "%.2f GB Used", bytesToGb(usedBytes)));
        binding.freeStorage.setText(String.format(Locale.getDefault(), "%.2f GB Free", bytesToGb(freeBytes)));
        animateProgress(usedPercent);
    }

    private void animateProgress(int targetProgress) {
        Handler handler = new Handler();
        int currentProgress = binding.storageProgress.getProgress();
        Runnable progressUpdater = new Runnable() {
            int progress = currentProgress;

            @Override
            public void run() {
                if (progress != targetProgress) {
                    progress += progress < targetProgress ? 1 : -1;
                    viewModel.lastProgress = progress;
                    binding.storageProgress.setProgress(progress);
                    binding.progressText.setText(progress + "%");
                    handler.postDelayed(this, 10);
                }
            }
        };
        handler.post(progressUpdater);
    }

    private double bytesToGb(long bytes) {
        return bytes / (1024.0 * 1024.0 * 1024.0);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("saved_progress", viewModel.lastProgress);
    }

    // ✅ NEW: Forward permission result to PermissionUtils (optional)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // If user grants permission, refresh UI
        if (requestCode == PermissionUtils.REQUEST_STORAGE_PERMISSION) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }

            if (granted) {
                displayStorageInfo();
            } else {
                showToast("Storage permission denied");
                startActivity(new Intent(this, PermissionActivity.class));
                finish();
            }
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
