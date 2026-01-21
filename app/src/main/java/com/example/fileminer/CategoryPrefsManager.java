package com.example.fileminer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class CategoryPrefsManager {

    private static final String PREF_NAME = "dynamic_sections_pref";
    private static final String KEY_CATEGORIES = "categories_json";

    public static void initDefaultCategoriesIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_CATEGORIES)) {
            resetToDefault(context);
        }
    }

    public static void resetToDefault(Context context) {
        ArrayList<CategoryModel> defaults = getDefaultCategories();
        saveCategories(context, defaults);
    }

    public static ArrayList<CategoryModel> getCategories(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_CATEGORIES, null);

        ArrayList<CategoryModel> list = new ArrayList<>();

        try {
            if (json == null) return list;

            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);

                String name = obj.getString("name");
                int iconRes = obj.getInt("iconRes");
                boolean isDefault = obj.optBoolean("isDefault", false);

                JSONArray extArray = obj.getJSONArray("extensions");
                ArrayList<String> exts = new ArrayList<>();
                for (int j = 0; j < extArray.length(); j++) {
                    exts.add(normalizeExtension(extArray.getString(j)));
                }

                list.add(new CategoryModel(name, exts, iconRes, isDefault));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public static void saveCategories(Context context, ArrayList<CategoryModel> categories) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        try {
            JSONArray array = new JSONArray();
            for (CategoryModel cat : categories) {
                JSONObject obj = new JSONObject();
                obj.put("name", cat.name);
                obj.put("iconRes", cat.iconRes);
                obj.put("isDefault", cat.isDefault);

                JSONArray extArray = new JSONArray();
                if (cat.extensions != null) {
                    for (String ext : cat.extensions) {
                        String cleaned = normalizeExtension(ext);
                        if (!cleaned.isEmpty()) extArray.put(cleaned);
                    }
                }

                obj.put("extensions", extArray);
                array.put(obj);
            }

            prefs.edit().putString(KEY_CATEGORIES, array.toString()).apply();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<String> getExtensionsForSection(Context context, String sectionName) {
        ArrayList<CategoryModel> categories = getCategories(context);
        for (CategoryModel cat : categories) {
            if (cat.name.equalsIgnoreCase(sectionName)) {
                return cat.extensions;
            }
        }
        return new ArrayList<>();
    }

    // ✅ Normalize: "pdf" => ".pdf"
    public static String normalizeExtension(String ext) {
        if (ext == null) return "";
        ext = ext.trim().toLowerCase();
        if (ext.isEmpty()) return "";
        if (!ext.startsWith(".")) ext = "." + ext;
        return ext;
    }

    // ✅ Remove extension from ALL sections (duplicate prevention)
    public static void removeExtensionFromAllSections(ArrayList<CategoryModel> categories, String ext) {
        if (categories == null) return;
        ext = normalizeExtension(ext);

        for (CategoryModel c : categories) {
            if (c.extensions == null) continue;
            c.extensions.remove(ext);
        }
    }

    // Default categories (cannot delete)
    private static ArrayList<CategoryModel> getDefaultCategories() {
        ArrayList<CategoryModel> list = new ArrayList<>();

        list.add(new CategoryModel("Photos",
                new ArrayList<String>() {{
                    add(".jpg"); add(".jpeg"); add(".png"); add(".webp"); add(".bmp");
                }},
                R.drawable.ic_photo,
                true
        ));

        list.add(new CategoryModel("Videos",
                new ArrayList<String>() {{
                    add(".mp4"); add(".mkv"); add(".avi"); add(".mov");
                }},
                R.drawable.ic_video,
                true
        ));

        list.add(new CategoryModel("Audio",
                new ArrayList<String>() {{
                    add(".mp3"); add(".wav"); add(".m4a"); add(".opus");
                }},
                R.drawable.ic_audio,
                true
        ));

        list.add(new CategoryModel("Documents",
                new ArrayList<String>() {{
                    add(".pdf"); add(".doc"); add(".docx"); add(".pptx"); add(".odt"); add(".xls"); add(".xlsx");
                }},
                R.drawable.ic_document,
                true
        ));

        return list;
    }
}
