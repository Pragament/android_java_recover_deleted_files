package com.example.fileminer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.fileminer.databinding.ActivityMain2Binding;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity2 extends AppCompatActivity {

    private ActivityMain2Binding binding;
    private StorageViewModel viewModel;

    // Dynamic categories list
    private ArrayList<CategoryModel> categoryList = new ArrayList<>();
    private CategoryAdapter categoryAdapter;

    // Matches your existing category logic for bottom nav
    private static final String[] FILE_TYPES = {
            "Photo", "Video", "Audio", "Document", "Deleted", "Hidden", "OtherFiles"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        super.onCreate(savedInstanceState);

        binding = ActivityMain2Binding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(StorageViewModel.class);
        restoreProgress(savedInstanceState);

        if (PermissionUtils.checkStoragePermission(this)) {
            displayStorageInfo();
        } else {
            startActivity(new Intent(this, PermissionActivity.class));
            finish();
            return;
        }

        // ✅ Initialize default categories once
        CategoryPrefsManager.initDefaultCategoriesIfNeeded(this);

        // ✅ Setup dynamic category recycler
        setupDynamicCategoryGrid();

        // Keep bottom nav same
        setupBottomNav();
    }

    // ✅ NEW: Dynamic categories section
    private void setupDynamicCategoryGrid() {
        categoryList = CategoryPrefsManager.getCategories(this);

        categoryAdapter = new CategoryAdapter(this, categoryList);

        binding.recyclerCategories.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerCategories.setAdapter(categoryAdapter);
    }

    // Bottom Navigation - unchanged logic
    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_deleted || id == R.id.nav_recover) {
                // Deleted list
                navigateToOldCategory(FILE_TYPES[4]);
                return true;
            } else if (id == R.id.nav_hidden) {
                navigateToOldCategory(FILE_TYPES[5]);
                return true;
            } else if (id == R.id.nav_other) {
                navigateToOldCategory(FILE_TYPES[6]);
                return true;
            } else if (id == R.id.nav_settings) {
                // ✅ Open manage categories
                startActivity(new Intent(this, ManageCategoriesActivity.class));
                return true;
            }
            return false;
        });
    }

    // Old navigation method preserved (for Deleted/Hidden/OtherFiles)
    private void navigateToOldCategory(String category) {
        Intent intent = new Intent(MainActivity2.this, RestoredFilesActivity.class);
        intent.putExtra("fileType", category);
        startActivity(intent);
    }

    // --- OLD LOGIC PRESERVED BELOW ---

    @Override
    protected void onResume() {
        super.onResume();

        if (!PermissionUtils.checkStoragePermission(this)) {
            startActivity(new Intent(this, PermissionActivity.class));
            finish();
            return;
        }

        // ✅ Reload categories when user returns from ManageCategoriesActivity
        if (binding != null) {
            setupDynamicCategoryGrid();
        }
    }

    private void restoreProgress(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            viewModel.lastProgress = savedInstanceState.getInt("saved_progress", 0);
        }
        binding.storageProgress.setProgress(viewModel.lastProgress);
        binding.progressText.setText(String.format(Locale.getDefault(), "%d%%", viewModel.lastProgress));
    }

    private void displayStorageInfo() {
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long totalBytes = stat.getTotalBytes();
            long freeBytes = stat.getAvailableBytes();
            long usedBytes = totalBytes - freeBytes;
            int usedPercent = totalBytes > 0 ? (int) ((usedBytes * 100) / totalBytes) : 0;

            binding.usedStorage.setText(String.format(Locale.getDefault(), "%.2f GB Used", bytesToGb(usedBytes)));
            binding.freeStorage.setText(String.format(Locale.getDefault(), "%.2f GB Free", bytesToGb(freeBytes)));
            animateProgress(usedPercent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void animateProgress(int targetProgress) {
        Handler handler = new Handler();
        int currentProgress = binding.storageProgress.getProgress();

        Runnable progressUpdater = new Runnable() {
            int progress = currentProgress;

            @Override
            public void run() {
                if (progress != targetProgress) {
                    progress += (progress < targetProgress) ? 1 : -1;
                    viewModel.lastProgress = progress;
                    binding.storageProgress.setProgress(progress);
                    binding.progressText.setText(String.format(Locale.getDefault(), "%d%%", progress));
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("saved_progress", viewModel.lastProgress);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
