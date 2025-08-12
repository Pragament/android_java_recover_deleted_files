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
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
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
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RestoredFilesActivity extends AppCompatActivity implements ToolbarUpdateListener, FileDeleteListener {

    private ActivityRestoredFilesBinding binding;

    private MediaAdapter adapter;
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

    // --- NEW ---
    // State variable to control the OCR filter.
    private boolean filterByTextInImage = false;
    // ML Kit TextRecognizer instance
    private TextRecognizer recognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityRestoredFilesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize the TextRecognizer. This should be done once.
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        restoredFiles = new ArrayList<>();
        selectedFiles = new ArrayList<>();
        fullMediaItemList = new ArrayList<>();
        adapter = new MediaAdapter(this, restoredFiles, this, this);
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
    }

    private boolean isImageFile(String path) {
        if (path == null) return false;
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".png") || lowerPath.endsWith(".bmp") || lowerPath.endsWith(".webp");
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
                startFileScan();
                break;
            case "Hidden":
                showHiddenFiles();
                break;
            case "OtherFiles":
                fetchOtherFiles();
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
                        if (filePath != null && !filePath.contains("/.trashed/") && !filePath.contains("/.recycle/") && !filePath.contains("/.trash/")
                                && !filePath.contains("/_.trashed/")) {
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
                        if (filePath != null && !filePath.contains("/.trashed/") && !filePath.contains("/.recycle/") && !filePath.contains("/.trash/")
                                && !filePath.contains("/_.trashed/")) {
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
                        if (filePath != null && !filePath.contains("/.trashed/") && !filePath.contains("/.recycle/") && !filePath.contains("/.trash/")
                                && !filePath.contains("/_.trashed/")) {
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

    private void startFileScan() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.gridView.setVisibility(View.GONE);

        new Thread(() -> {
            deletedFiles = new ArrayList<>();
            Set<String> seenPaths = new HashSet<>();

            File rootDir = Environment.getExternalStorageDirectory();
            searchTrashedFiles(rootDir, 0, seenPaths);

            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.gridView.setVisibility(View.VISIBLE);

                if (deletedFiles.isEmpty()) {
                    Toast.makeText(this, "No deleted files found!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, deletedFiles.size() + " deleted files found!", Toast.LENGTH_SHORT).show();
                }

                restoredFiles.clear();
                fullMediaItemList.clear();

                ArrayList<MediaItem> itemsToCache = new ArrayList<>();

                for (File file : deletedFiles) {
                    MediaItem item = new MediaItem(file.getName(), file.getAbsolutePath());
                    restoredFiles.add(item);
                    fullMediaItemList.add(item);
                    itemsToCache.add(item);
                }

                if (!itemsToCache.isEmpty()) {
                    FileCache.getInstance().put(fileType, itemsToCache);
                }

                sortFiles();
                adapter = new MediaAdapter(this, restoredFiles, RestoredFilesActivity.this, this);
                binding.gridView.setAdapter(adapter);
            });

        }).start();
    }

    private void searchTrashedFiles(File dir, int depth, Set<String> seenPaths) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || depth > 10 || deletedFiles.size() >= MAX_FILES)
            return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String absPath = file.getAbsolutePath();
            if (seenPaths.contains(absPath)) continue;
            seenPaths.add(absPath);

            if (file.isDirectory()) {
                searchTrashedFiles(file, depth + 1, seenPaths);
            } else if (isDeletedFile(file)) {
                deletedFiles.add(file);
            }
        }
    }

    private boolean isTrashFolder(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.startsWith(".trashed-") || name.startsWith(".trashed") || name.equals(".recycle") || name.equals(".trash") || name.equals("_.trashed");

    }

    private boolean isDeletedFile(File file) {
        File parentDir = file.getParentFile();
        return parentDir != null && isTrashFolder(parentDir) || file.getName().startsWith(".trashed-");
    }

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

            adapter = new MediaAdapter(this, restoredFiles, this, this);
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
        String[] photoExtensions = {
                ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".tiff"
        };

        String[] videoExtensions = {
                ".mp4", ".mkv", ".avi", ".mov", ".flv", ".pdf", ".ppt", ".pptx", ".odt", ".doc", ".docx", ".xls", ".xlsx"
        };

        String fileName = file.getName().toLowerCase(Locale.ROOT);

        for (String ext : photoExtensions) {
            if (fileName.endsWith(ext)) return true;
        }
        for (String ext : videoExtensions) {
            if (fileName.endsWith(ext)) return true;
        }
        return false;
    }

    private void fetchOtherFiles() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.gridView.setVisibility(View.GONE);

        new Thread(() -> {
            File directory = new File("/storage/emulated/0/");
            restoredFiles = new ArrayList<>();
            fullMediaItemList = new ArrayList<>();

            searchFiles(directory);
            sortFiles();

            fullMediaItemList.addAll(restoredFiles);
            runOnUiThread(() -> {
                adapter = new MediaAdapter(this, restoredFiles, this, this);
                binding.gridView.setAdapter(adapter);
                binding.progressBar.setVisibility(View.GONE);
                binding.gridView.setVisibility(View.VISIBLE);
            });
        }).start();
    }

    private void searchFiles(File dir) {
        try {
            if (dir != null && dir.isDirectory() && !isExcludedFolder(dir) && !dir.getName().startsWith(".")) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && !file.getName().startsWith(".") &&
                                !isExcludedFileType(file) && !isTooSmall(file)) {
                            restoredFiles.add(new MediaItem(file.getName(), file.getAbsolutePath()));
                        } else if (file.isDirectory() && file.canRead()) {
                            searchFiles(file);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isExcludedFolder(File file) {
        String path = file.getAbsolutePath();
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
                path.contains("/Logs/") || path.contains("_.trashed") || path.contains(".trashed") ||
                path.contains(".recycle") || path.contains(".trash");

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
        if (filePath.endsWith(".mp4") || filePath.endsWith(".mkv")) {
            return "video/*";
        } else if (filePath.endsWith(".mp3") || filePath.endsWith(".wav") || filePath.endsWith(".m4a")) {
            return "audio/*";
        } else if (filePath.endsWith(".jpg") || filePath.endsWith(".png")) {
            return "image/*";
        } else if (filePath.endsWith(".pdf")) {
            return "application/pdf";
        } else if (filePath.endsWith(".doc") || filePath.endsWith(".docx")) {
            return "application/msword";
        } else if (filePath.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else if (filePath.endsWith(".odt")) {
            return "application/vnd.oasis.opendocument.text";
        }
        return "/";
    }

    @Override
    public void updateSelectionToolbar() {
        AllFeaturesUtils.updateSelectionToolbar(restoredFiles, binding.selectionToolbar);
    }

    // --- MODIFIED to fix the compilation error ---
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

            // NOTE: The constructor call is reverted to its original signature to fix the compilation error.
            // This means the 'filterByTextInImage' toggle cannot be controlled from the UI yet.
            // To fully enable the feature, 'SearchBottomSheet.java' must be updated to accept the new boolean
            // and its listener must be updated to return the user's choice for that flag.
            SearchBottomSheet bottomSheet = new SearchBottomSheet(
                    this,
                    selectedSearchType,
                    isCaseSensitive,
                    excludedFolders,
                    excludedExtensions,
                    fileType,
                    fileNameFilterType,
                    // The 'filterByTextInImage' parameter and its corresponding lambda parameter are removed to match the original signature.
                    (searchType, caseSensitive, folders, extensions, newFileNameFilterType) -> {
                        // This is the original listener signature.
                        selectedSearchType = searchType;
                        isCaseSensitive = caseSensitive;
                        excludedFolders = folders;
                        excludedExtensions = extensions;
                        fileNameFilterType = newFileNameFilterType;
                        // The 'filterByTextInImage' state is NOT updated from the sheet.

                        // Immediately apply all filters with the new settings.
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

        // 1. First, apply the "text in image" (OCR) filter if it's active
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

        // 2. Next, apply the "filename content" filter on the result of the OCR filter.
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

        // 3. Finally, perform the rest of the filtering on the result of the previous steps.
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
                file -> moveToTrash(file)
        );
    }

    private void deleteSelectedFiles() {
        AllFeaturesUtils.deleteSelectedFiles(
                this,
                restoredFiles,
                fullMediaItemList,
                adapter,
                file -> moveToTrash(file)
        );
    }

    private boolean moveToTrash(File file) {
        File trashDir = new File(Environment.getExternalStorageDirectory(), "_.trashed");
        if (!trashDir.exists()) trashDir.mkdirs();
        File destFile = new File(trashDir, file.getName());
        return file.renameTo(destFile);
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
}