package com.example.fileminer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.util.ArrayList;

/**
 * MediaItem.java
 * Model class
 */
public class MediaItem {

    String name;
    String path;
    long size;
    long dateModified;
    private boolean isSelected = false;

    public Boolean hasText = null;

    // ✅ NEW: For restore feature
    public String originalPath = null;    // where file was before deleting
    public boolean isDeletedItem = false; // true only for Deleted tab items

    // ✅ NEW: clean display name (for Deleted tab)
    public String displayName = null;   // name shown in UI (without timestamp)
    public String originalName = null;  // extracted original file name

    public MediaItem(String name, String path) {
        this.name = name;
        this.path = path;

        // ✅ NEW: Set clean display name for UI
        this.originalName = getCleanDeletedName(name);
        this.displayName = this.originalName;

        try {
            File file = new File(path);
            if (file.exists()) {
                this.size = file.length();
                this.dateModified = file.lastModified();
            } else {
                Log.e("MediaItem", "File not found: " + path);
                this.size = 0;
                this.dateModified = 0;
            }
        } catch (Exception e) {
            Log.e("MediaItem", "Error reading file: " + path, e);
            this.size = 0;
            this.dateModified = 0;
        }
    }

    // ✅ NEW: Remove timestamp prefix like "1705_" and return clean name
    public static String getCleanDeletedName(String fileName) {
        if (fileName == null) return "";

        int underscoreIndex = fileName.indexOf("_");

        // if underscore exists and prefix before underscore is only digits => remove it
        if (underscoreIndex > 0) {
            String prefix = fileName.substring(0, underscoreIndex);
            if (prefix.matches("\\d+")) {
                return fileName.substring(underscoreIndex + 1);
            }
        }

        return fileName;
    }

    // ✅ NEW: getters/setters (optional but clean)
    public String getOriginalPath() {
        return originalPath;
    }

    public void setOriginalPath(String originalPath) {
        this.originalPath = originalPath;
    }

    public boolean isDeletedItem() {
        return isDeletedItem;
    }

    public void setDeletedItem(boolean deletedItem) {
        isDeletedItem = deletedItem;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }

    public String getFilePath() {
        return path;
    }

    public void setFilePath(String newPath) {
        this.path = newPath;
    }

    /**
     * MediaAdapter.java
     * Shows MediaItem in GridView
     */
    public static class MediaAdapter extends ArrayAdapter<MediaItem> {

        private boolean showPath = false;
        private final ToolbarUpdateListener toolbarListener;
        private final FileDeleteListener fileDeleteListener;

        public MediaAdapter(Context context,
                            ArrayList<MediaItem> mediaItems,
                            ToolbarUpdateListener toolbarListener,
                            FileDeleteListener fileDeleteListener) {
            super(context, R.layout.media_list_item, mediaItems);
            this.toolbarListener = toolbarListener;
            this.fileDeleteListener = fileDeleteListener;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

            View listItem = convertView;

            if (listItem == null) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                listItem = inflater.inflate(R.layout.media_list_item, parent, false);
            }

            TextView text1 = listItem.findViewById(R.id.mediaName);
            ImageView imageView = listItem.findViewById(R.id.mediaThumbnail);
            ImageView shareButton = listItem.findViewById(R.id.shareButton);
            ImageView deleteButton = listItem.findViewById(R.id.deleteButton);

            // ✅ NEW: restore button
            ImageView restoreButton = listItem.findViewById(R.id.restoreButton);

            CheckBox checkBox = listItem.findViewById(R.id.checkBox);

            MediaItem currentItem = getItem(position);

            if (currentItem != null && currentItem.path != null) {

                if (showPath) {
                    File file = new File(currentItem.path);
                    File parentFolderFile = file.getParentFile();
                    if (parentFolderFile != null) {
                        text1.setText(parentFolderFile.getName() + "/");
                    } else {
                        // ✅ FIX: show displayName if available
                        text1.setText(currentItem.displayName != null ? currentItem.displayName : currentItem.name);
                    }
                } else {
                    // ✅ FIX: Show clean name in UI instead of timestamp name
                    text1.setText(currentItem.displayName != null ? currentItem.displayName : currentItem.name);
                }

                checkBox.setOnCheckedChangeListener(null);
                checkBox.setChecked(currentItem.isSelected());

                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    currentItem.setSelected(isChecked);
                    if (toolbarListener != null) {
                        toolbarListener.updateSelectionToolbar();
                    }
                });

                loadThumbnail(imageView, currentItem);

                shareButton.setOnClickListener(v -> {
                    try {
                        File file = new File(currentItem.path);
                        Uri uri = FileProvider.getUriForFile(
                                getContext(),
                                getContext().getPackageName() + ".provider",
                                file
                        );

                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType(getMimeType(currentItem.path));
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        getContext().startActivity(Intent.createChooser(shareIntent, "Share file via"));
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Error sharing file", Toast.LENGTH_SHORT).show();
                    }
                });

                listItem.setOnClickListener(v -> openFile(currentItem));

                deleteButton.setOnClickListener(v -> {
                    if (fileDeleteListener != null) {
                        fileDeleteListener.deleteFile(currentItem);
                    }
                });

                // ✅ Restore button only for Deleted items
                if (currentItem.isDeletedItem) {
                    restoreButton.setVisibility(View.VISIBLE);

                    restoreButton.setOnClickListener(v -> {
                        if (fileDeleteListener != null) {
                            fileDeleteListener.restoreFile(currentItem);
                        }
                    });

                } else {
                    restoreButton.setVisibility(View.GONE);
                    restoreButton.setOnClickListener(null);
                }
            }

            return listItem;
        }

        public void setShowPath(boolean showPath) {
            this.showPath = showPath;
            notifyDataSetChanged();
        }

        private void loadThumbnail(ImageView imageView, MediaItem currentItem) {
            String filePath = currentItem.path.toLowerCase();

            if (filePath.endsWith(".jpg") || filePath.endsWith(".png") || filePath.endsWith(".jpeg")
                    || filePath.endsWith(".webp") || filePath.endsWith(".gif") || filePath.endsWith(".bmp")
                    || filePath.endsWith(".tiff") || filePath.endsWith(".svg")) {

                Glide.with(getContext())
                        .load(new File(currentItem.path))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(imageView);

            } else if (filePath.endsWith(".mp4") || filePath.endsWith(".mkv")) {

                Glide.with(getContext())
                        .asBitmap()
                        .load(Uri.fromFile(new File(currentItem.path)))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.ic_video)
                        .error(R.drawable.ic_video)
                        .into(imageView);

            } else if (filePath.endsWith(".mp3") || filePath.endsWith(".wav")
                    || filePath.endsWith(".m4a") || filePath.endsWith(".opus")) {

                imageView.setImageResource(R.drawable.ic_audio);

            } else if (filePath.endsWith(".pdf")) {
                imageView.setImageResource(R.drawable.ic_pdf);

            } else if (filePath.endsWith(".pptx")) {
                imageView.setImageResource(R.drawable.ic_ppt);

            } else if (filePath.endsWith(".odt") || filePath.endsWith(".xls") || filePath.endsWith(".xlsx")) {
                imageView.setImageResource(R.drawable.ic_excel);

            } else {
                imageView.setImageResource(R.drawable.ic_file);
            }
        }

        private void openFile(MediaItem currentItem) {
            try {
                File file = new File(currentItem.path);
                Uri uri = FileProvider.getUriForFile(
                        getContext(),
                        getContext().getPackageName() + ".provider",
                        file
                );

                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(uri, getMimeType(currentItem.path));
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                getContext().startActivity(Intent.createChooser(openIntent, "Open with"));
            } catch (Exception e) {
                Toast.makeText(getContext(), "No app found to open this file", Toast.LENGTH_SHORT).show();
            }
        }

        private String getMimeType(String filePath) {
            String extension = filePath.substring(filePath.lastIndexOf('.') + 1);
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            String type = mime.getMimeTypeFromExtension(extension);
            return type != null ? type : "*/*";
        }
    }
}
