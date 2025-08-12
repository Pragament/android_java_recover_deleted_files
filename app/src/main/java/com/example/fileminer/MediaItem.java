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
import java.lang.ref.WeakReference;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.util.ArrayList;

public class MediaItem {
    String name;
    String path;
    long size;
    long dateModified;
    private boolean isSelected = false;

    // null = not yet processed, true = contains text, false = does not contain text
    public Boolean hasText = null;

    MediaItem(String name, String path) {
        this.name = name;
        this.path = path;
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
            Log.e("MediaItem", "Error retrieving file size or dateModified for file: " + path, e);
            this.size = 0;
            this.dateModified = 0;
        }
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
}
//------------- MediaAdapter class
class MediaAdapter extends ArrayAdapter<MediaItem> {
    private boolean showPath = false;
    private final WeakReference<Context> contextRef;
    private final ToolbarUpdateListener toolbarListener;
    private final FileDeleteListener fileDeleteListener;

    public MediaAdapter(Context context,
                        ArrayList<MediaItem> mediaItems,
                        ToolbarUpdateListener toolbarListener,
                        FileDeleteListener fileDeleteListener) {
        super(context, R.layout.media_list_item, mediaItems);
        this.contextRef = new WeakReference<>(context.getApplicationContext()); // Safer way
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
        CheckBox checkBox = listItem.findViewById(R.id.checkBox);

        MediaItem currentItem = getItem(position);

        if (currentItem != null && currentItem.path != null) {
            text1.setText(currentItem.name);

            if (showPath) {
                File file = new File(currentItem.path);
                File parentFolderFile = file.getParentFile();

                if (parentFolderFile != null) {
                    String parentFolder = parentFolderFile.getName();
                    text1.setText(parentFolder + "/");
                } else {
                    text1.setText(currentItem.name);
                }
            } else {
                text1.setText(currentItem.name);
            }

            // Handle checkbox state
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(currentItem.isSelected());

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                currentItem.setSelected(isChecked);
                if (toolbarListener != null) {
                    toolbarListener.updateSelectionToolbar();
                }
            });

            loadThumbnail(imageView, currentItem);

            // Share Button functionality
            shareButton.setOnClickListener(v -> {
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

                try {
                    getContext().startActivity(Intent.createChooser(shareIntent, "Share file via"));
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Error sharing file", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            });

            // Open file on item click
            listItem.setOnClickListener(v -> openFile(currentItem));

            // Delete Button functionality
            deleteButton.setOnClickListener(v -> {
                if (fileDeleteListener != null) {
                    fileDeleteListener.deleteFile(currentItem);
                }
            });

        }

        return listItem;
    }
    public void setShowPath(boolean showPath) {
        this.showPath = showPath;
        notifyDataSetChanged();
    }

    private void loadThumbnail(ImageView imageView, MediaItem currentItem) {
        String filePath = currentItem.path;

        if (filePath.endsWith(".jpg") || filePath.endsWith(".png") || filePath.endsWith(".jpeg")
                ||filePath.endsWith(".webp") || filePath.endsWith(".gif") || filePath.endsWith(".bmp")
        || filePath.endsWith(".tiff") || filePath.endsWith(".svg") ) {
            Glide.with(getContext())
                    .load(new File(filePath))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(imageView);
        } else if (filePath.endsWith(".mp4") || filePath.endsWith(".mkv")) {
            Glide.with(getContext())
                    .asBitmap()
                    .load(Uri.fromFile(new File(filePath)))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_video)
                    .error(R.drawable.ic_video)
                    .into(imageView);
        } else if (filePath.endsWith(".mp3") || filePath.endsWith(".wav") || filePath.endsWith(".m4a") || filePath.endsWith(".opus")) {
            imageView.setImageResource(R.drawable.ic_audio);
        } else if (filePath.endsWith(".pdf")) {
            imageView.setImageResource(R.drawable.ic_pdf);
        } else if (filePath.endsWith(".pptx")) {
            imageView.setImageResource(R.drawable.ic_ppt);
        } else if (filePath.endsWith(".odt")) {
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
            e.printStackTrace();
        }
    }
    private String getMimeType(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf('.') + 1);
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getMimeTypeFromExtension(extension);
    }

}