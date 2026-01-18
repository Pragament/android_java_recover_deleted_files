package com.example.fileminer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class AllFeaturesUtils {

    // ✅ NEW: SharedPreferences keys for restore feature
    private static final String PREF_TRASH = "trash_restore_pref";
    private static final String KEY_MAP_PREFIX = "trash_map_"; // key = trash_map_<trashAbsolutePath>

    // =========================================================
    // ✅ NEW: Clean Deleted file name helper
    // =========================================================
    public static String getCleanDeletedName(String fileName) {
        if (fileName == null) return "";

        int underscoreIndex = fileName.indexOf("_");

        // Example: "1705_photo.jpg" -> "photo.jpg"
        if (underscoreIndex > 0) {
            String prefix = fileName.substring(0, underscoreIndex);
            if (prefix.matches("\\d+")) {
                return fileName.substring(underscoreIndex + 1);
            }
        }
        return fileName;
    }

    // =========================================================
    // 1) Selection Toolbar (Show / Hide)
    // =========================================================
    public static void updateSelectionToolbar(List<MediaItem> fileList, Toolbar selectionToolbar) {
        boolean anySelected = false;
        for (MediaItem item : fileList) {
            if (item.isSelected()) {
                anySelected = true;
                break;
            }
        }
        selectionToolbar.setVisibility(anySelected ? View.VISIBLE : View.GONE);
    }

    // =========================================================
    // 2) Sorting
    // =========================================================
    public static void sortFiles(List<MediaItem> fileList, String sortType, boolean isAscending) {
        if ("name".equals(sortType)) {
            Collections.sort(fileList, (a, b) -> isAscending ?
                    a.name.compareToIgnoreCase(b.name) :
                    b.name.compareToIgnoreCase(a.name));
        } else if ("size".equals(sortType)) {
            Collections.sort(fileList, (a, b) -> {
                if (a.size == 0 && b.size == 0) return 0;
                else if (a.size == 0) return isAscending ? 1 : -1;
                else if (b.size == 0) return isAscending ? -1 : 1;
                return isAscending ? Long.compare(a.size, b.size) : Long.compare(b.size, a.size);
            });
        } else if ("time".equals(sortType)) {
            Collections.sort(fileList, (a, b) ->
                    isAscending ? Long.compare(a.dateModified, b.dateModified)
                            : Long.compare(b.dateModified, a.dateModified));
        }
    }

    // =========================================================
    // 3) Filtering (Search)
    // =========================================================
    public static void filterFiles(
            String query,
            List<String> excludedFolders,
            List<String> excludedExtensions,
            List<MediaItem> baseList,
            List<MediaItem> restoredFiles,
            boolean isCaseSensitive,
            String selectedSearchType,
            TextView noResultsText,
            GridView listView,
            BaseAdapter adapter,
            Runnable sortRunnable
    ) {
        if (query == null) query = "";
        String searchQuery = query.trim();
        List<MediaItem> filteredList = new ArrayList<>();

        if (!isCaseSensitive) {
            searchQuery = searchQuery.toLowerCase(Locale.ROOT);
        }

        for (MediaItem item : baseList) {
            if (item == null || item.name == null) continue;

            File file = new File(item.path);

            // ✅ Skip non-existing files (important for deleted files)
            if (!file.exists()) continue;

            String fileName = item.name;
            String filePath = item.path;
            String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")) : "";

            if (!isCaseSensitive) {
                fileName = fileName.toLowerCase(Locale.ROOT);
                if (filePath != null) filePath = filePath.toLowerCase(Locale.ROOT);
                extension = extension.toLowerCase(Locale.ROOT);
            }

            if (shouldExclude(item, excludedFolders)) continue;
            if (excludedExtensions.contains(extension)) continue;

            if (searchQuery.isEmpty()) {
                filteredList.add(item);
            } else {
                switch (selectedSearchType) {
                    case "Starts With":
                        if (fileName.startsWith(searchQuery)) filteredList.add(item);
                        break;
                    case "Ends With":
                        if (fileName.endsWith(searchQuery)) filteredList.add(item);
                        break;
                    case "Path":
                        if (filePath != null && filePath.contains(searchQuery)) filteredList.add(item);
                        break;
                    case "Contains":
                    default:
                        if (fileName.contains(searchQuery)) filteredList.add(item);
                        break;
                }
            }
        }

        restoredFiles.clear();
        restoredFiles.addAll(filteredList);

        if (sortRunnable != null) sortRunnable.run();

        noResultsText.post(() -> {
            if (restoredFiles.isEmpty()) {
                noResultsText.setVisibility(View.VISIBLE);
                listView.setVisibility(View.GONE);
            } else {
                noResultsText.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
            }

            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
    }

    // =========================================================
    // 4) Exclude Folder Helper
    // =========================================================
    public static boolean shouldExclude(MediaItem item, List<String> excludedFolders) {
        if (excludedFolders == null || excludedFolders.isEmpty()) return false;

        File file = new File(item.path);
        File parentFolder = file.getParentFile();
        if (parentFolder != null) {
            String folderName = parentFolder.getName();
            for (String exclude : excludedFolders) {
                if (folderName.equalsIgnoreCase(exclude)) {
                    return true;
                }
            }
        }
        return false;
    }

    // =========================================================
    // 5) Load File Names (for UI list)
    // =========================================================
    public static void loadFileList(List<MediaItem> restoredFiles, List<String> fileList) {
        fileList.clear();
        for (MediaItem item : restoredFiles) {
            fileList.add(item.name);
        }
    }

    // =========================================================
    // 6) DELETE SINGLE FILE (FIXED)
    // =========================================================
    public static void deleteFile(Context context,
                                  MediaItem item,
                                  List<MediaItem> restoredFiles,
                                  List<MediaItem> fullMediaItemList,
                                  BaseAdapter adapter,
                                  Function<File, Boolean> moveToTrashFunc) {

        String fileType = getCurrentFileType(context);

        new AlertDialog.Builder(context)
                .setTitle("Delete File")
                .setMessage("Are you sure you want to delete this file?")
                .setPositiveButton("Yes, Delete", (dialog, which) -> {

                    File file = new File(item.path);
                    boolean success;

                    // ✅ Deleted category = permanent delete
                    if ("Deleted".equals(fileType)) {
                        success = file.exists() && file.delete();
                    } else {
                        // ✅ Other categories = move to trash
                        success = file.exists() && moveToTrashFunc.apply(file);
                    }

                    if (success) {

                        // ✅ IMPORTANT FIX:
                        // Remove from lists using path match (not object reference)
                        removeByPath(restoredFiles, item.path);
                        removeByPath(fullMediaItemList, item.path);

                        // ✅ Refresh UI
                        if (adapter != null) adapter.notifyDataSetChanged();

                        Toast.makeText(context,
                                "Deleted".equals(fileType) ? "File permanently deleted" : "File moved to trash",
                                Toast.LENGTH_SHORT).show();

                    } else {
                        Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    // =========================================================
    // 7) DELETE SELECTED FILES (FIXED)
    // =========================================================
    public static void deleteSelectedFiles(Context context,
                                           List<MediaItem> restoredFiles,
                                           List<MediaItem> fullMediaItemList,
                                           BaseAdapter adapter,
                                           Function<File, Boolean> moveToTrashFunc) {

        String fileType = getCurrentFileType(context);

        new AlertDialog.Builder(context)
                .setTitle("Delete Selected Files")
                .setMessage("Are you sure you want to delete the selected files?")
                .setPositiveButton("Yes, Delete", (dialog, which) -> {

                    ArrayList<String> deletedPaths = new ArrayList<>();

                    for (MediaItem item : new ArrayList<>(restoredFiles)) {
                        if (!item.isSelected()) continue;

                        File file = new File(item.path);
                        boolean success;

                        if ("Deleted".equals(fileType)) {
                            success = file.exists() && file.delete();
                        } else {
                            success = file.exists() && moveToTrashFunc.apply(file);
                        }

                        if (success) {
                            deletedPaths.add(item.path);
                        } else {
                            Toast.makeText(context, "Failed to delete: " + item.name, Toast.LENGTH_SHORT).show();
                        }
                    }

                    // ✅ Remove from both lists after deletion
                    for (String path : deletedPaths) {
                        removeByPath(restoredFiles, path);
                        removeByPath(fullMediaItemList, path);
                    }

                    if (adapter != null) adapter.notifyDataSetChanged();

                    Toast.makeText(context,
                            deletedPaths.isEmpty()
                                    ? "No files were deleted."
                                    : ("Deleted".equals(fileType) ? "Files permanently deleted!" : "Selected files moved to trash!"),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // =========================================================
    // 8) Helper: Remove by Path (MOST IMPORTANT FIX)
    // =========================================================
    private static void removeByPath(List<MediaItem> list, String path) {
        if (list == null || path == null) return;

        for (int i = list.size() - 1; i >= 0; i--) {
            MediaItem m = list.get(i);
            if (m != null && path.equals(m.path)) {
                list.remove(i);
            }
        }
    }

    // =========================================================
    // 9) Intent Helper
    // =========================================================
    private static String getCurrentFileType(Context context) {
        if (context instanceof RestoredFilesActivity) {
            Intent intent = ((RestoredFilesActivity) context).getIntent();
            return intent.getStringExtra("fileType");
        }
        return "";
    }

    // =========================================================
    // 10) Select All
    // =========================================================
    public static void selectAllFiles(List<MediaItem> mediaItemList, boolean select) {
        for (MediaItem item : mediaItemList) {
            item.setSelected(select);
        }
    }

    // =========================================================
    // 11) Move Files (Your existing logic - unchanged)
    // =========================================================
    public static void moveSelectedFiles(Context context, List<MediaItem> fullMediaItemList, Runnable onMoveComplete, Runnable loadFileList) {
        List<MediaItem> selectedItems = new ArrayList<>();
        for (MediaItem item : fullMediaItemList) {
            if (item.isSelected()) {
                selectedItems.add(item);
            }
        }

        if (selectedItems.isEmpty()) {
            Toast.makeText(context, "No files selected to move", Toast.LENGTH_SHORT).show();
            return;
        }

        File rootDir = Environment.getExternalStorageDirectory();
        openFolderPicker(context, rootDir, selectedItems, onMoveComplete, loadFileList);
    }

    private static void openFolderPicker(Context context, File currentDir, List<MediaItem> selectedItems, Runnable onMoveComplete, Runnable loadFileList) {
        File[] subFoldersArr = currentDir.listFiles(File::isDirectory);
        if (subFoldersArr == null) subFoldersArr = new File[0];

        final File[] subFolders = subFoldersArr;

        List<String> options = new ArrayList<>();
        for (File folder : subFolders) {
            options.add(folder.getName());
        }

        options.add("📂 Create New Folder");
        options.add("✅ Select This Folder");

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Select Folder in:\n" + currentDir.getAbsolutePath());
        builder.setItems(options.toArray(new String[0]), (dialog, which) -> {
            if (which < subFolders.length) {
                openFolderPicker(context, subFolders[which], selectedItems, onMoveComplete, loadFileList);
            } else if (which == subFolders.length) {
                showCreateSubfolderDialog(context, currentDir, selectedItems, onMoveComplete, loadFileList);
            } else {
                moveFilesToFolder(context, selectedItems, currentDir, onMoveComplete, loadFileList);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private static void showCreateSubfolderDialog(Context context, File parentFolder, List<MediaItem> selectedItems, Runnable onMoveComplete, Runnable loadFileList) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Create New Folder in:\n" + parentFolder.getAbsolutePath());

        final EditText input = new EditText(context);
        input.setHint("Folder name");
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (!folderName.isEmpty()) {
                File newFolder = new File(parentFolder, folderName);
                if (!newFolder.exists()) {
                    if (newFolder.mkdirs()) {
                        Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show();
                        openFolderPicker(context, newFolder, selectedItems, onMoveComplete, loadFileList);
                    } else {
                        Toast.makeText(context, "Failed to create folder", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(context, "Folder already exists", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private static void moveFilesToFolder(Context context, List<MediaItem> selectedItems, File destinationFolder, Runnable onMoveComplete, Runnable loadFileList) {
        boolean anyFileMoved = false;

        for (MediaItem item : selectedItems) {
            File sourceFile = new File(item.getFilePath());
            File destFile = new File(destinationFolder, sourceFile.getName());

            try {
                if (copyFile(sourceFile, destFile)) {
                    if (sourceFile.delete()) {
                        item.setFilePath(destFile.getAbsolutePath());
                        anyFileMoved = true;
                    } else {
                        Toast.makeText(context, "Copied but failed to delete: " + sourceFile.getName(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(context, "Failed to copy: " + sourceFile.getName(), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(context, "Error moving: " + sourceFile.getName(), Toast.LENGTH_SHORT).show();
            }
        }

        if (anyFileMoved) {
            Toast.makeText(context, "Files moved successfully", Toast.LENGTH_SHORT).show();
            if (onMoveComplete != null) onMoveComplete.run();
            if (loadFileList != null) loadFileList.run();
        }
    }

    private static boolean copyFile(File source, File dest) throws IOException {
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

    // =========================================================
    // 12) Search Setup
    // =========================================================
    public static void setupSearch(Menu menu, Context context, FileFilterCallback callback) {
        MenuItem searchItem = menu.findItem(R.id.action_search);

        if (searchItem != null) {
            androidx.appcompat.widget.SearchView searchView = new androidx.appcompat.widget.SearchView(context);
            searchView.setQueryHint("Search Files...");
            searchView.setIconifiedByDefault(true);

            EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (searchEditText != null) {
                searchEditText.setTextColor(Color.WHITE);
                searchEditText.setHintTextColor(Color.LTGRAY);
            }

            searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    callback.onFilter(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    callback.onFilter(newText);
                    return true;
                }
            });

            searchItem.setActionView(searchView);
            searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        }

        MenuItem filterItem = menu.findItem(R.id.action_filter);
        if (filterItem != null) {
            filterItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
    }

    public interface FileFilterCallback {
        void onFilter(String query);
    }

    // =========================================================
    // 13) Hide / Show Duplicates (unchanged)
    // =========================================================
    public static void hideDuplicates(Context context,
                                      List<MediaItem> fullMediaItemList,
                                      List<MediaItem> currentFilteredBaseList,
                                      List<MediaItem> restoredFiles,
                                      Runnable sortFiles,
                                      BaseAdapter adapter) {
        new HideDuplicatesTask(context, fullMediaItemList, currentFilteredBaseList, restoredFiles, sortFiles, adapter).execute();
    }

    public static void showOnlyDuplicates(Context context,
                                          List<MediaItem> fullMediaItemList,
                                          List<MediaItem> currentFilteredBaseList,
                                          List<MediaItem> restoredFiles,
                                          List<MediaItem> duplicateList,
                                          Runnable sortFiles,
                                          BaseAdapter adapter) {
        new ShowOnlyDuplicatesTask(context, fullMediaItemList, currentFilteredBaseList, restoredFiles, duplicateList, sortFiles, adapter).execute();
    }

    private static class HideDuplicatesTask extends AsyncTask<Void, Void, List<MediaItem>> {
        private final Context context;
        private final List<MediaItem> fullList, filteredList, restoredList;
        private final Runnable sortCallback;
        private final BaseAdapter adapter;

        public HideDuplicatesTask(Context context, List<MediaItem> fullList, List<MediaItem> filteredList,
                                  List<MediaItem> restoredList, Runnable sortCallback, BaseAdapter adapter) {
            this.context = context;
            this.fullList = fullList;
            this.filteredList = filteredList;
            this.restoredList = restoredList;
            this.sortCallback = sortCallback;
            this.adapter = adapter;
        }

        @Override
        protected void onPreExecute() {
            Toast.makeText(context, "Hiding duplicates, please wait...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected List<MediaItem> doInBackground(Void... voids) {
            Map<Long, List<MediaItem>> sizeMap = new HashMap<>();
            for (MediaItem item : fullList) {
                if (item != null && item.path != null) {
                    File file = new File(item.path);
                    if (file.exists()) {
                        sizeMap.computeIfAbsent(file.length(), k -> new ArrayList<>()).add(item);
                    }
                }
            }

            Set<String> seenHashes = new HashSet<>();
            List<MediaItem> uniqueFiles = new ArrayList<>();

            for (List<MediaItem> group : sizeMap.values()) {
                if (group.size() == 1) {
                    uniqueFiles.add(group.get(0));
                } else {
                    for (MediaItem item : group) {
                        String hash = DuplicateUtils.getFileHash(item.path);
                        if (hash != null && seenHashes.add(hash)) {
                            uniqueFiles.add(item);
                        }
                    }
                }
            }

            return uniqueFiles;
        }

        @Override
        protected void onPostExecute(List<MediaItem> result) {
            filteredList.clear();
            filteredList.addAll(result);

            restoredList.clear();
            restoredList.addAll(result);

            sortCallback.run();
            adapter.notifyDataSetChanged();

            Toast.makeText(context, result.isEmpty() ? "No unique files found" : "Duplicates Hidden", Toast.LENGTH_SHORT).show();
        }
    }

    private static class ShowOnlyDuplicatesTask extends AsyncTask<Void, Void, List<MediaItem>> {
        private final WeakReference<Context> contextRef;
        private final List<MediaItem> fullList;
        private final List<MediaItem> filteredList;
        private final List<MediaItem> restoredList;
        private final List<MediaItem> duplicateList;
        private final Runnable sortCallback;
        private final BaseAdapter adapter;

        public ShowOnlyDuplicatesTask(Context context,
                                      List<MediaItem> fullList,
                                      List<MediaItem> filteredList,
                                      List<MediaItem> restoredList,
                                      List<MediaItem> duplicateList,
                                      Runnable sortCallback,
                                      BaseAdapter adapter) {
            this.contextRef = new WeakReference<>(context);
            this.fullList = fullList;
            this.filteredList = filteredList;
            this.restoredList = restoredList;
            this.duplicateList = duplicateList;
            this.sortCallback = sortCallback;
            this.adapter = adapter;
        }

        @Override
        protected void onPreExecute() {
            Context context = contextRef.get();
            if (context != null) {
                Toast.makeText(context, "Finding duplicates, please wait...", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected List<MediaItem> doInBackground(Void... voids) {
            Map<Long, List<MediaItem>> sizeMap = new HashMap<>();

            for (MediaItem item : fullList) {
                if (item != null && item.path != null) {
                    File file = new File(item.path);
                    if (file.exists()) {
                        sizeMap.computeIfAbsent(file.length(), k -> new ArrayList<>()).add(item);
                    }
                }
            }

            Map<String, Integer> hashCountMap = new HashMap<>();
            Map<String, MediaItem> hashToItem = new HashMap<>();
            List<MediaItem> duplicates = new ArrayList<>();

            for (List<MediaItem> group : sizeMap.values()) {
                if (group.size() > 1) {
                    for (MediaItem item : group) {
                        String hash = DuplicateUtils.getFileHash(item.path);
                        if (hash != null) {
                            int count = hashCountMap.getOrDefault(hash, 0);
                            hashCountMap.put(hash, count + 1);

                            if (count == 1) {
                                duplicates.add(hashToItem.get(hash));
                                duplicates.add(item);
                            } else if (count > 1) {
                                duplicates.add(item);
                            } else {
                                hashToItem.put(hash, item);
                            }
                        }
                    }
                }
            }

            return duplicates;
        }

        @Override
        protected void onPostExecute(List<MediaItem> result) {
            Context context = contextRef.get();
            if (context == null) return;

            duplicateList.clear();
            duplicateList.addAll(result);

            filteredList.clear();
            filteredList.addAll(result);

            restoredList.clear();
            restoredList.addAll(result);

            if (sortCallback != null) sortCallback.run();

            if (adapter != null) adapter.notifyDataSetChanged();

            Toast.makeText(context,
                    result.isEmpty() ? "No duplicate files found" : "Showing Only Duplicates",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================
    // ✅ NEW: Save mapping for restore
    // =========================================================
    private static void saveTrashMapping(Context context, String trashPath, String originalPath) {
        context.getSharedPreferences(PREF_TRASH, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MAP_PREFIX + trashPath, originalPath)
                .apply();
    }

    // =========================================================
    // ✅ NEW: Get original path back
    // =========================================================
    public static String getOriginalPathFromTrash(Context context, String trashPath) {
        return context.getSharedPreferences(PREF_TRASH, Context.MODE_PRIVATE)
                .getString(KEY_MAP_PREFIX + trashPath, null);
    }

    // =========================================================
    // ✅ NEW: Remove mapping after restore
    // =========================================================
    private static void removeTrashMapping(Context context, String trashPath) {
        context.getSharedPreferences(PREF_TRASH, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_MAP_PREFIX + trashPath)
                .apply();
    }

    // =========================================================
    // ✅ UPDATED: Common Trash Method (Use everywhere)
    // =========================================================
    public static boolean moveToTrash(Context context, File file) {
        try {
            File trashDir = new File(Environment.getExternalStorageDirectory(), "_.trashed");

            if (!trashDir.exists()) {
                trashDir.mkdirs();
            }

            String uniqueName = System.currentTimeMillis() + "_" + file.getName();
            File destFile = new File(trashDir, uniqueName);

            String originalPath = file.getAbsolutePath();

            boolean moved = file.renameTo(destFile);

            if (moved) {
                saveTrashMapping(context, destFile.getAbsolutePath(), originalPath);
            }

            return moved;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================
    // ✅ NEW: Restore Method
    // =========================================================
    public static boolean restoreFromTrash(Context context, MediaItem item) {
        try {
            if (item == null || item.path == null) return false;

            File trashFile = new File(item.path);
            if (!trashFile.exists()) return false;

            String originalPath = item.originalPath;

            if (originalPath == null) {
                originalPath = getOriginalPathFromTrash(context, trashFile.getAbsolutePath());
            }

            if (originalPath == null) {
                Toast.makeText(context, "Original path not found!", Toast.LENGTH_SHORT).show();
                return false;
            }

            File originalFile = new File(originalPath);
            File originalFolder = originalFile.getParentFile();

            if (originalFolder != null && !originalFolder.exists()) {
                originalFolder.mkdirs();
            }

            File restoreTarget = originalFile;
            if (restoreTarget.exists()) {
                String name = originalFile.getName();
                String newName = System.currentTimeMillis() + "_" + name;
                restoreTarget = new File(originalFolder, newName);
            }

            boolean restored = trashFile.renameTo(restoreTarget);

            if (restored) {
                removeTrashMapping(context, trashFile.getAbsolutePath());
                Toast.makeText(context, "File restored successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Restore failed!", Toast.LENGTH_SHORT).show();
            }

            return restored;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
