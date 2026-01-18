package com.example.fileminer;

import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.fileminer.databinding.ActivityRestoredFilesBinding;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RestoredFilesActivity extends AppCompatActivity implements ToolbarUpdateListener, FileDeleteListener {

    private ActivityRestoredFilesBinding binding;

    private MediaItem.MediaAdapter adapter;
    private ArrayList<MediaItem> restoredFiles;
    private ArrayList<MediaItem> selectedFiles;
    private List<String> fileList = new ArrayList<>();
    private List<MediaItem> fullMediaItemList = new ArrayList<>();
    private boolean isCaseSensitive = false;
    private boolean showPath = false;
    private List<String> excludedFolders = new ArrayList<>();
    private List<String> excludedExtensions = new ArrayList<>();
    private boolean isShowingDuplicates = false;
    private List<MediaItem> duplicateList = new ArrayList<>();
    private List<MediaItem> currentFilteredBaseList = new ArrayList<>();
    private String currentQuery = "";
    private String currentSort = "time";
    private boolean isAscending = false;
    private String selectedSearchType = "Contains";
    private String fileType = "Photo";
    private String fileNameFilterType = "Both";
    List<File> deletedFiles;
    private static final int MAX_FILES = 500;
    private final List<String> hiddenFilesList = new ArrayList<>();

    // --- OCR ---
    private boolean filterByTextInImage = false;
    private TextRecognizer recognizer;

    // ✅ NEW: Background scan executor (for Deleted + OtherFiles)
    private final ScanExecutor scanExecutor = new ScanExecutor();

    // ✅ NEW: Prevent multiple scans at same time
    private volatile boolean isScanRunning = false;

    // ✅ NEW: Progress tracking
    private volatile int scannedCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityRestoredFilesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());



        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        restoredFiles = new ArrayList<>();
        selectedFiles = new ArrayList<>();
        fullMediaItemList = new ArrayList<>();

        adapter = new MediaItem.MediaAdapter(this, restoredFiles, this, this);
        binding.gridView.setAdapter(adapter);

        binding.gridView.setOnItemClickListener((parent, view, position, id) -> {
            MediaItem item = restoredFiles.get(position);
            openFile(item.path);
        });

        View root = findViewById(R.id.main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                v.setPadding(0, topInset, 0, 0);
                return insets;
            });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), root);
                if (isLightMode()) {
                    controller.setAppearanceLightStatusBars(true);
                    getWindow().setStatusBarColor(Color.WHITE);
                } else {
                    controller.setAppearanceLightStatusBars(false);
                    getWindow().setStatusBarColor(Color.BLACK);
                }
            } else {
                getWindow().setStatusBarColor(Color.BLACK);
            }
        } else {
            root.setPadding(0, dpToPx(24), 0, 0);
        }

        setSupportActionBar(binding.mainToolbar);
        setupSelectionToolbar();

        Intent intent = getIntent();
        fileType = intent.getStringExtra("fileType");
        Log.d("RestoredFilesActivity", "Received fileType: " + fileType);

        loadData();
        updateSelectionToolbar();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // ✅ NEW: stop scan to avoid memory leaks/crashes
        scanExecutor.shutdown();
    }

    private boolean isImageFile(String path) {
        if (path == null) return false;
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".png")
                || lowerPath.endsWith(".bmp") || lowerPath.endsWith(".webp");
    }

    private void startOcrForImages() {
        if ("Photo".equals(fileType)) {
            Log.d("OCR_PROCESS", "Starting OCR scan for " + fullMediaItemList.size() + " items.");
            for (MediaItem item : fullMediaItemList) {
                if (isImageFile(item.path) && item.hasText == null) {
                    processImageForText(item);
                }
            }
        }
    }

    private void processImageForText(final MediaItem item) {
        File file = new File(item.path);
        if (!file.exists()) {
            item.hasText = false;
            return;
        }

        try {
            InputImage image = InputImage.fromFilePath(this, Uri.fromFile(file));
            Task<Text> result = recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        item.hasText = !visionText.getText().trim().isEmpty();
                        if (item.hasText) {
                            Log.d("OCR_PROCESS", "Text FOUND in: " + item.path);
                        }
                    })
                    .addOnFailureListener(e -> {
                        item.hasText = false;
                        Log.e("OCR_PROCESS", "Text recognition failed for: " + item.path, e);
                    });

        } catch (IOException e) {
            item.hasText = false;
            Log.e("OCR_PROCESS", "Failed to create InputImage for: " + item.path, e);
        }
    }

    private void loadData() {
        if (fileType == null) return;

        // ✅ NEW: Safety permission check (scan requires storage permission)
        if (!PermissionUtils.checkStoragePermission(this)) {
            Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        List<MediaItem> cachedFiles = FileCache.getInstance().get(fileType);
        if (cachedFiles != null && !cachedFiles.isEmpty()) {
            restoredFiles.clear();
            restoredFiles.addAll(cachedFiles);

            fullMediaItemList.clear();
            fullMediaItemList.addAll(cachedFiles);

            adapter.notifyDataSetChanged();
            binding.progressBar.setVisibility(View.GONE);
            startOcrForImages();
            return;
        }

        switch (fileType) {
            case "Photo":
                new LoadMediaFilesTask(this).execute(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                break;
            case "Video":
                new LoadMediaFilesTask(this).execute(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                break;
            case "Audio":
                new LoadMediaFilesTask(this).execute(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
                break;
            case "Document":
                new LoadDocumentFilesTask(this).execute();
                break;
            case "Deleted":
                startFileScan(); // improved
                break;
            case "Hidden":
                showHiddenFiles();
                break;
            case "OtherFiles":
                fetchOtherFiles(); // improved
                break;
            default:
                new LoadAllFilesTask(this).execute();
                break;
        }
    }

    private boolean isLightMode() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode != Configuration.UI_MODE_NIGHT_YES;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void setupSelectionToolbar() {
        binding.selectionToolbar.inflateMenu(R.menu.selection_menu);

        // ✅ FIX: menu exists now, safe to call
        updateRestoreMenuVisibility();

        binding.selectionToolbar.setTitleTextColor(getResources().getColor(android.R.color.black));
        binding.selectionToolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.deleteSelected) {
                binding.selectionToolbar.setTitle("Delete Files");
                deleteSelectedFiles();
                return true;
            } else if (id == R.id.moveSelected) {
                binding.selectionToolbar.setTitle("Move Files");
                moveSelectedFiles();
                return true;
            } else if (id == R.id.restoreSelected) {
                binding.selectionToolbar.setTitle("Restore Files");
                restoreSelectedFiles();
                return true;
            } else if (id == R.id.selectAll) {
                boolean selectAll = !item.isChecked();
                item.setChecked(selectAll);
                item.setTitle(selectAll ? "Deselect All File" : "Select All File");
                binding.selectionToolbar.setTitle(selectAll ? "All Files Selected" : "Select Files");
                selectAllFiles(selectAll);
                adapter.notifyDataSetChanged();
                return true;
            }
            return false;
        });
    }

    // ------------------------ AsyncTask (MediaStore) remains same ------------------------

    private static class LoadMediaFilesTask extends AsyncTask<Uri, Void, ArrayList<MediaItem>> {
        private final WeakReference<RestoredFilesActivity> activityRef;

        LoadMediaFilesTask(RestoredFilesActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            RestoredFilesActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                activity.binding.progressBar.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected ArrayList<MediaItem> doInBackground(Uri... uris) {
            ArrayList<MediaItem> mediaItems = new ArrayList<>();
            RestoredFilesActivity activity = activityRef.get();
            if (activity == null) return mediaItems;

            Uri contentUri = uris[0];
            String[] projection = {
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DISPLAY_NAME
            };

            try (Cursor cursor = activity.getContentResolver().query(contentUri, projection, null, null, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String filePath = cursor.getString(0);
                        String displayName = cursor.getString(1);

                        if (filePath != null && !filePath.contains("/.trashed/") && !filePath.contains("/.recycle/")
                                && !filePath.contains("/.trash/") && !filePath.contains("/_.trashed/")) {
                            mediaItems.add(new MediaItem(displayName, filePath));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("RestoredFilesActivity", "Error loading media files", e);
            }

            return mediaItems;
        }

        @Override
        protected void onPostExecute(ArrayList<MediaItem> mediaItems) {
            RestoredFilesActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                FileCache.getInstance().put(activity.fileType, mediaItems);
                activity.restoredFiles.clear();
                activity.restoredFiles.addAll(mediaItems);

                activity.fullMediaItemList.clear();
                activity.fullMediaItemList.addAll(mediaItems);

                activity.sortFiles();
                activity.adapter.notifyDataSetChanged();
                activity.binding.progressBar.setVisibility(View.GONE);
                activity.startOcrForImages();
            }
        }
    }

    private static class LoadDocumentFilesTask extends AsyncTask<Void, Void, ArrayList<MediaItem>> {
        private final WeakReference<RestoredFilesActivity> activityRef;

        LoadDocumentFilesTask(RestoredFilesActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            RestoredFilesActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                activity.binding.progressBar.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected ArrayList<MediaItem> doInBackground(Void... voids) {
            ArrayList<MediaItem> mediaItems = new ArrayList<>();
            RestoredFilesActivity activity = activityRef.get();
            if (activity == null) return mediaItems;

            Uri contentUri = MediaStore.Files.getContentUri("external");
            String[] projection = {
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DISPLAY_NAME
            };
            String selection = MediaStore.MediaColumns.MIME_TYPE + " IN (?, ?, ?, ?, ?)";
            String[] selectionArgs = {
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.oasis.opendocument.text"
            };

            try (Cursor cursor = activity.getContentResolver().query(contentUri, projection, selection, selectionArgs, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String filePath = cursor.getString(0);
                        String displayName = cursor.getString(1);
                        if (filePath != null && !filePath.contains("/.trashed/") && !filePath.contains("/.recycle/")
                                && !filePath.contains("/.trash/") && !filePath.contains("/_.trashed/")) {
                            mediaItems.add(new MediaItem(displayName, filePath));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("RestoredFilesActivity", "Error loading document files", e);
            }

            return mediaItems;
        }

        @Override
        protected void onPostExecute(ArrayList<MediaItem> mediaItems) {
            RestoredFilesActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                activity.restoredFiles.clear();
                activity.restoredFiles.addAll(mediaItems);

                activity.fullMediaItemList.clear();
                activity.fullMediaItemList.addAll(mediaItems);

                activity.sortFiles();
                activity.adapter.notifyDataSetChanged();
                activity.binding.progressBar.setVisibility(View.GONE);
            }
        }
    }

    private static class LoadAllFilesTask extends AsyncTask<Void, Void, ArrayList<MediaItem>> {
        private final WeakReference<RestoredFilesActivity> activityRef;

        LoadAllFilesTask(RestoredFilesActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            RestoredFilesActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                activity.binding.progressBar.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected ArrayList<MediaItem> doInBackground(Void... voids) {
            ArrayList<MediaItem> mediaItems = new ArrayList<>();
            RestoredFilesActivity activity = activityRef.get();
            if (activity == null) return mediaItems;

            Uri contentUri = MediaStore.Files.getContentUri("external");
            String[] projection = {
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DISPLAY_NAME
            };

            try (Cursor cursor = activity.getContentResolver().query(contentUri, projection, null, null, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String filePath = cursor.getString(0);
                        String displayName = cursor.getString(1);
                        if (filePath != null && !filePath.contains("/.trashed/") && !filePath.contains("/.recycle/")
                                && !filePath.contains("/.trash/") && !filePath.contains("/_.trashed/")) {
                            mediaItems.add(new MediaItem(displayName, filePath));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("RestoredFilesActivity", "Error loading all files", e);
            }

            return mediaItems;
        }

        @Override
        protected void onPostExecute(ArrayList<MediaItem> mediaItems) {
            RestoredFilesActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                activity.restoredFiles.clear();
                activity.restoredFiles.addAll(mediaItems);

                activity.fullMediaItemList.clear();
                activity.fullMediaItemList.addAll(mediaItems);

                activity.sortFiles();
                activity.adapter.notifyDataSetChanged();
                activity.binding.progressBar.setVisibility(View.GONE);
            }
        }
    }

    // ------------------------ IMPROVED SCANNING: Deleted Files ------------------------

    private void startFileScan() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.gridView.setVisibility(View.GONE);

        new Thread(() -> {

            // ✅ Always scan only our app trash folder
            File trashDir = new File(Environment.getExternalStorageDirectory(), "_.trashed");

            deletedFiles = new ArrayList<>();

            if (trashDir.exists() && trashDir.isDirectory()) {
                File[] files = trashDir.listFiles();

                if (files != null) {
                    for (File f : files) {
                        if (f != null && f.isFile()) {
                            deletedFiles.add(f);
                        }
                    }
                }
            }

            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.gridView.setVisibility(View.VISIBLE);

                restoredFiles.clear();
                fullMediaItemList.clear();

                ArrayList<MediaItem> itemsToCache = new ArrayList<>();

                for (File file : deletedFiles) {
                    MediaItem item = new MediaItem(file.getName(), file.getAbsolutePath());

                    // ✅ mark as deleted item
                    item.isDeletedItem = true;

                    // ✅ NEW: clean UI name (hide timestamp like 1705_)
                    item.displayName = AllFeaturesUtils.getCleanDeletedName(item.name);

                    // ✅ load original path for restore
                    item.originalPath = AllFeaturesUtils.getOriginalPathFromTrash(this, file.getAbsolutePath());

                    restoredFiles.add(item);
                    fullMediaItemList.add(item);
                    itemsToCache.add(item);
                }

                // ✅ Cache deleted list
                FileCache.getInstance().put(fileType, itemsToCache);

                adapter.notifyDataSetChanged();

                if (deletedFiles.isEmpty()) {
                    Toast.makeText(this, "No deleted files found!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, deletedFiles.size() + " deleted files found!", Toast.LENGTH_SHORT).show();
                }
            });

        }).start();
    }

    private boolean isTrashFolder(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.startsWith(".trashed-") || name.startsWith(".trashed") || name.equals(".recycle") || name.equals(".trash") || name.equals("_.trashed");
    }

    // ------------------------ Hidden Files ------------------------

    private void showHiddenFiles() {
        File directory = Environment.getExternalStorageDirectory();
        if (directory != null && directory.exists()) {
            hiddenFilesList.clear();
            scanHiddenFolders(directory);
        }

        if (hiddenFilesList.isEmpty()) {
            Toast.makeText(this, "No hidden photos or videos found", Toast.LENGTH_SHORT).show();
        } else {
            restoredFiles.clear();
            for (String path : hiddenFilesList) {
                File file = new File(path);
                String name = file.getName();
                long size = file.length();
                long modified = file.lastModified();

                MediaItem item = new MediaItem(name, path);
                item.size = size;
                item.dateModified = modified;

                restoredFiles.add(item);
            }

            fullMediaItemList.clear();
            fullMediaItemList.addAll(restoredFiles);

            sortFiles();

            adapter = new MediaItem.MediaAdapter(this, restoredFiles, this, this);
            binding.gridView.setAdapter(adapter);
            adapter.notifyDataSetChanged();
        }
    }

    private void scanHiddenFolders(File directory) {
        if (directory == null || !directory.exists() || !directory.canRead()) {
            Log.e("HiddenFiles", "Cannot access directory: " + (directory != null ? directory.getAbsolutePath() : "null"));
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && file.getName().startsWith(".")) {
                    Log.d("HiddenFiles", "Hidden folder found: " + file.getAbsolutePath());
                    listHiddenFiles(file);
                }
            }
        }
    }

    private void listHiddenFiles(File folder) {
        if (folder == null || !folder.exists() || !folder.canRead()) {
            Log.e("HiddenFiles", "Cannot read folder: " + (folder != null ? folder.getAbsolutePath() : "null"));
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            Log.e("HiddenFiles", "Files list is null for: " + folder.getAbsolutePath());
            return;
        }

        for (File file : files) {
            if (file.isFile() && isPhotoOrVideo(file)) {
                hiddenFilesList.add(file.getAbsolutePath());
                Log.d("HiddenFiles", "Hidden photo or video found: " + file.getAbsolutePath());
            }
        }
    }

    private boolean isPhotoOrVideo(File file) {
        if (file == null || !file.exists()) return false;

        String[] photoExtensions = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".tiff"};
        String[] videoExtensions = {".mp4", ".mkv", ".avi", ".mov", ".flv"};

        String fileName = file.getName().toLowerCase(Locale.ROOT);

        for (String ext : photoExtensions) {
            if (fileName.endsWith(ext)) return true;
        }
        for (String ext : videoExtensions) {
            if (fileName.endsWith(ext)) return true;
        }
        return false;
    }

    // ------------------------ IMPROVED SCANNING: Other Files ------------------------

    private void fetchOtherFiles() {
        if (isScanRunning) {
            Toast.makeText(this, "Scan already running...", Toast.LENGTH_SHORT).show();
            return;
        }

        isScanRunning = true;

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.gridView.setVisibility(View.GONE);

        scanExecutor.run(() -> {
            File directory = new File("/storage/emulated/0/");
            ArrayList<MediaItem> tempList = new ArrayList<>();

            searchFiles(directory, tempList);

            runOnUiThread(() -> {
                isScanRunning = false;

                restoredFiles.clear();
                restoredFiles.addAll(tempList);

                fullMediaItemList.clear();
                fullMediaItemList.addAll(tempList);

                sortFiles();

                adapter = new MediaItem.MediaAdapter(this, restoredFiles, this, this);
                binding.gridView.setAdapter(adapter);

                binding.progressBar.setVisibility(View.GONE);
                binding.gridView.setVisibility(View.VISIBLE);
            });
        });
    }

    // ✅ UPDATED: use tempList param instead of global list to reduce sync issues
    private void searchFiles(File dir, ArrayList<MediaItem> output) {
        if (Thread.currentThread().isInterrupted()) return;
        if (output.size() >= MAX_FILES) return;

        try {
            if (dir != null && dir.isDirectory() && !isExcludedFolder(dir) && !dir.getName().startsWith(".")) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (Thread.currentThread().isInterrupted()) return;
                        if (output.size() >= MAX_FILES) return;

                        if (file.isFile() && !file.getName().startsWith(".") &&
                                !isExcludedFileType(file) && !isTooSmall(file)) {
                            output.add(new MediaItem(file.getName(), file.getAbsolutePath()));
                        } else if (file.isDirectory() && file.canRead()) {
                            searchFiles(file, output);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------ Exclusions ------------------------

    private boolean isExcludedFolder(File file) {
        String path = file.getAbsolutePath();

        // ✅ UPDATED: faster scanning by skipping heavy folders
        return path.contains("/WhatsApp/Media/.Statuses") ||
                path.contains("/Android/media/com.whatsapp/WhatsApp/Media/.Statuses") ||
                path.contains("/Android/data/") ||
                path.contains("/Android/obb/") ||
                path.contains("/.thumbnails") ||
                path.contains("/.cache") ||
                path.contains("/Telegram/") ||
                path.contains("/Instagram/") ||
                path.contains("/MIUI/") ||
                path.contains("/com.miui.backup/") ||
                path.contains("/com.miui.backup/") ||
                path.contains("/Logs/") ||
                path.contains("_.trashed") ||
                path.contains(".trashed") ||
                path.contains(".recycle") ||
                path.contains(".trash");
    }

    private boolean isTooSmall(File file) {
        return file.length() < 10 * 1024;
    }

    private boolean isExcludedFileType(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mkv") ||
                name.endsWith(".pdf") || name.endsWith(".mp3") || name.endsWith(".wav") ||
                name.endsWith(".odt") || name.endsWith(".pptx") || name.endsWith(".doc") ||
                name.endsWith(".docx");
    }

    private void openFile(String filePath) {
        if (filePath == null) return;
        File file = new File(filePath);
        Uri uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, getMimeType(filePath));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.e("RestoredFilesActivity", "No suitable app found to open this file", e);
        }
    }

    private String getMimeType(String filePath) {
        String lower = filePath.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".mp4") || lower.endsWith(".mkv")) {
            return "video/*";
        } else if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a")) {
            return "audio/*";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg")) {
            return "image/*";
        } else if (lower.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "application/msword";
        } else if (lower.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else if (lower.endsWith(".odt")) {
            return "application/vnd.oasis.opendocument.text";
        }
        return "*/*";
    }


    @Override
    public void updateSelectionToolbar() {

        boolean anySelected = false;
        for (MediaItem item : restoredFiles) {
            if (item != null && item.isSelected()) {
                anySelected = true;
                break;
            }
        }

        // show/hide selection toolbar
        binding.selectionToolbar.setVisibility(anySelected ? View.VISIBLE : View.GONE);

        // ✅ Restore visible only in Deleted tab AND when selected
        MenuItem restoreItem = binding.selectionToolbar.getMenu().findItem(R.id.restoreSelected);
        if (restoreItem != null) {
            restoreItem.setVisible("Deleted".equals(fileType) && anySelected);
        }

        // Optional: Move not needed in Deleted tab
        MenuItem moveItem = binding.selectionToolbar.getMenu().findItem(R.id.moveSelected);
        if (moveItem != null) {
            moveItem.setVisible(!"Deleted".equals(fileType) && anySelected);
        }

        // Delete always visible when selected
        MenuItem deleteItem = binding.selectionToolbar.getMenu().findItem(R.id.deleteSelected);
        if (deleteItem != null) {
            deleteItem.setVisible(anySelected);
        }

        // SelectAll visible always
        MenuItem selectAllItem = binding.selectionToolbar.getMenu().findItem(R.id.selectAll);
        if (selectAllItem != null) {
            selectAllItem.setVisible(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.sortByName) {
            currentSort = "name";
            sortFiles();
            adapter.notifyDataSetChanged();
            return true;
        } else if (id == R.id.sortBySize) {
            currentSort = "size";
            sortFiles();
            adapter.notifyDataSetChanged();
            return true;
        } else if (id == R.id.sortByTime) {
            currentSort = "time";
            sortFiles();
            adapter.notifyDataSetChanged();
            return true;
        } else if (id == R.id.sortOrderToggle) {
            isAscending = !isAscending;
            item.setTitle(isAscending ? "Ascending" : "Descending");
            sortFiles();
            adapter.notifyDataSetChanged();
            return true;
        } else if (id == R.id.action_refresh) {
            Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show();
            FileCache.getInstance().clear(fileType);
            loadData();
            return true;
        } else if (id == R.id.hideDuplicates) {
            hideDuplicates();
            return true;
        } else if (id == R.id.showOnlyDuplicates) {
            showOnlyDuplicates();
            return true;
        } else if (id == R.id.showPathToggle) {
            showPath = !item.isChecked();
            item.setChecked(showPath);
            adapter.setShowPath(showPath);
            return true;
        } else if (id == R.id.action_filter) {
            loadFileList();

            SearchBottomSheet bottomSheet = new SearchBottomSheet(
                    this,
                    selectedSearchType,
                    isCaseSensitive,
                    excludedFolders,
                    excludedExtensions,
                    fileType,
                    fileNameFilterType,
                    (searchType, caseSensitive, folders, extensions, newFileNameFilterType) -> {
                        selectedSearchType = searchType;
                        isCaseSensitive = caseSensitive;
                        excludedFolders = folders;
                        excludedExtensions = extensions;
                        fileNameFilterType = newFileNameFilterType;

                        filterFiles(currentQuery, excludedFolders, excludedExtensions);
                    }
            );

            bottomSheet.show(getSupportFragmentManager(), "SearchBottomSheet");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void filterFiles(String query, List<String> excludedFolders, List<String> excludedExtensions) {
        List<MediaItem> baseList;
        if (currentFilteredBaseList != null && !currentFilteredBaseList.isEmpty()) {
            baseList = new ArrayList<>(currentFilteredBaseList);
        } else if (isShowingDuplicates) {
            baseList = new ArrayList<>(duplicateList);
        } else {
            baseList = new ArrayList<>(fullMediaItemList);
        }

        List<MediaItem> afterOcrFilterList = new ArrayList<>();
        if (filterByTextInImage) {
            for (MediaItem item : baseList) {
                if (Boolean.TRUE.equals(item.hasText)) {
                    afterOcrFilterList.add(item);
                }
            }
        } else {
            afterOcrFilterList.addAll(baseList);
        }

        List<MediaItem> nameFilteredList = new ArrayList<>();
        if (!"Both".equals(fileNameFilterType)) {
            for (MediaItem item : afterOcrFilterList) {
                boolean hasAlphabeticalChars = item.name.matches(".*[a-zA-Z].*");

                if ("With Text".equals(fileNameFilterType) && hasAlphabeticalChars) {
                    nameFilteredList.add(item);
                } else if ("Without Text".equals(fileNameFilterType) && !hasAlphabeticalChars) {
                    nameFilteredList.add(item);
                }
            }
        } else {
            nameFilteredList.addAll(afterOcrFilterList);
        }

        AllFeaturesUtils.filterFiles(
                query,
                excludedFolders,
                excludedExtensions,
                nameFilteredList,
                restoredFiles,
                isCaseSensitive,
                selectedSearchType,
                binding.noResultsText,
                binding.gridView,
                adapter,
                this::sortFiles
        );
    }

    private void sortFiles() {
        AllFeaturesUtils.sortFiles(restoredFiles, currentSort, isAscending);
    }

    private void loadFileList() {
        AllFeaturesUtils.loadFileList(restoredFiles, fileList);
    }

    @Override
    public void deleteFile(MediaItem item) {
        AllFeaturesUtils.deleteFile(
                this,
                item,
                restoredFiles,
                fullMediaItemList,
                adapter,
                // ✅ FIX 2: use common trash method
                file -> AllFeaturesUtils.moveToTrash(this, file)
        );
    }

    private void deleteSelectedFiles() {
        AllFeaturesUtils.deleteSelectedFiles(
                this,
                restoredFiles,
                fullMediaItemList,
                adapter,
                // ✅ FIX 2: use common trash method
                file -> AllFeaturesUtils.moveToTrash(this, file)
        );
    }

    // ✅ NEW: Restore file callback (for Deleted tab)
    @Override
    public void restoreFile(MediaItem item) {

        boolean restored = AllFeaturesUtils.restoreFromTrash(this, item);

        if (restored) {
            // ✅ Remove restored file from Deleted list UI
            restoredFiles.remove(item);

            for (int i = fullMediaItemList.size() - 1; i >= 0; i--) {
                if (fullMediaItemList.get(i).path.equals(item.path)) {
                    fullMediaItemList.remove(i);
                }
            }

            adapter.notifyDataSetChanged();

            // Optional: refresh cache
            FileCache.getInstance().clear("Deleted");
        }
    }

    private void selectAllFiles(boolean select) {
        AllFeaturesUtils.selectAllFiles(fullMediaItemList, select);
        updateSelectionToolbar();
    }

    private void moveSelectedFiles() {
        AllFeaturesUtils.moveSelectedFiles(
                this,
                fullMediaItemList,
                this::updateSelectionToolbar,
                this::loadFileList
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.sort_menu, menu);
        AllFeaturesUtils.setupSearch(menu, this, query -> {
            currentQuery = query;
            filterFiles(query, excludedFolders, excludedExtensions);
        });
        return true;
    }

    private void hideDuplicates() {
        AllFeaturesUtils.hideDuplicates(
                this,
                fullMediaItemList,
                currentFilteredBaseList,
                restoredFiles,
                this::sortFiles,
                adapter
        );
    }

    private void showOnlyDuplicates() {
        AllFeaturesUtils.showOnlyDuplicates(
                this,
                fullMediaItemList,
                currentFilteredBaseList,
                restoredFiles,
                duplicateList,
                this::sortFiles,
                adapter
        );
    }

    private boolean copyFile(File source, File dest) {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {

            byte[] buffer = new byte[1024];
            int length;

            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    private void restoreSelectedFiles() {

        if (!"Deleted".equals(fileType)) {
            Toast.makeText(this, "Restore available only in Deleted tab", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<MediaItem> selectedRestoreList = new ArrayList<>();

        for (MediaItem item : new ArrayList<>(restoredFiles)) {
            if (item != null && item.isSelected()) {
                selectedRestoreList.add(item);
            }
        }

        if (selectedRestoreList.isEmpty()) {
            Toast.makeText(this, "No files selected to restore", Toast.LENGTH_SHORT).show();
            return;
        }

        int restoredCount = 0;

        for (MediaItem item : selectedRestoreList) {
            boolean restored = AllFeaturesUtils.restoreFromTrash(this, item);

            if (restored) {
                restoredCount++;

                restoredFiles.remove(item);

                for (int i = fullMediaItemList.size() - 1; i >= 0; i--) {
                    if (fullMediaItemList.get(i).path.equals(item.path)) {
                        fullMediaItemList.remove(i);
                    }
                }
            }
        }

        adapter.notifyDataSetChanged();
        FileCache.getInstance().clear("Deleted");

        Toast.makeText(this, restoredCount + " files restored!", Toast.LENGTH_SHORT).show();
    }

    private void updateRestoreMenuVisibility() {
        if (binding == null || binding.selectionToolbar == null) return;

        MenuItem restoreItem = binding.selectionToolbar.getMenu().findItem(R.id.restoreSelected);
        if (restoreItem != null) {
            restoreItem.setVisible("Deleted".equals(fileType));
        }
    }


}
