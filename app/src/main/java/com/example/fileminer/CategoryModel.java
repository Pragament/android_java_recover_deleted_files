package com.example.fileminer;

import java.io.Serializable;
import java.util.ArrayList;

public class CategoryModel implements Serializable {
    public String name;
    public ArrayList<String> extensions;
    public int iconRes;

    // ✅ NEW: default sections cannot be deleted
    public boolean isDefault = false;

    public CategoryModel(String name, ArrayList<String> extensions, int iconRes) {
        this.name = name;
        this.extensions = extensions;
        this.iconRes = iconRes;
    }

    public CategoryModel(String name, ArrayList<String> extensions, int iconRes, boolean isDefault) {
        this.name = name;
        this.extensions = extensions;
        this.iconRes = iconRes;
        this.isDefault = isDefault;
    }
}
