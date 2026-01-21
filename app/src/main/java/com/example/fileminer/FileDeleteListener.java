package com.example.fileminer;

public interface FileDeleteListener {
    void deleteFile(MediaItem item);

    // ✅ NEW: Restore deleted file from trash
    void restoreFile(MediaItem item);
}
