package com.example.fileminer;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class ManageCategoryAdapter extends RecyclerView.Adapter<ManageCategoryAdapter.VH> {

    public interface OnCategoryUpdatedListener {
        void onUpdated(ArrayList<CategoryModel> updatedList);
    }

    private final Context context;
    private final ArrayList<CategoryModel> categories;
    private final OnCategoryUpdatedListener listener;

    public ManageCategoryAdapter(Context context,
                                 ArrayList<CategoryModel> categories,
                                 OnCategoryUpdatedListener listener) {
        this.context = context;
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_manage_category, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CategoryModel item = categories.get(position);

        holder.title.setText(item.name);
        holder.icon.setImageResource(item.iconRes);
        holder.extensions.setText(joinExtensions(item.extensions));

        holder.btnEdit.setOnClickListener(v -> showOptionsDialog(position));
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    private String joinExtensions(ArrayList<String> exts) {
        if (exts == null || exts.isEmpty()) return "No extensions";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < exts.size(); i++) {
            sb.append(exts.get(i));
            if (i != exts.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    // ================================
    // MAIN OPTIONS DIALOG
    // ================================
    private void showOptionsDialog(int position) {
        CategoryModel item = categories.get(position);

        ArrayList<String> options = new ArrayList<>();
        options.add("Add Extension");
        options.add("Remove Extension");

        if (!item.isDefault) {
            options.add("Rename Section");
            options.add("Delete Section");
        }

        String[] optionArr = options.toArray(new String[0]);

        new AlertDialog.Builder(context)
                .setTitle(item.name)
                .setItems(optionArr, (dialog, which) -> {
                    String selected = optionArr[which];

                    if (selected.equals("Add Extension")) {
                        showAddExtensionDialog(position);
                    } else if (selected.equals("Remove Extension")) {
                        showRemoveExtensionDialog(position);
                    } else if (selected.equals("Rename Section")) {
                        showRenameDialog(position);
                    } else if (selected.equals("Delete Section")) {
                        showDeleteDialog(position);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // ================================
    // ADD EXTENSION (moves from other sections)
    // ================================
    private void showAddExtensionDialog(int position) {
        CategoryModel item = categories.get(position);

        EditText input = new EditText(context);
        input.setHint("Example: .heic");
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(context)
                .setTitle("Add Extension")
                .setMessage("Add extension to: " + item.name)
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {

                    String raw = input.getText().toString().trim();
                    String ext = CategoryPrefsManager.normalizeExtension(raw);

                    if (ext.isEmpty() || ext.equals(".")) {
                        Toast.makeText(context, "Invalid extension!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // ✅ Remove from all sections first (no duplicates allowed)
                    CategoryPrefsManager.removeExtensionFromAllSections(categories, ext);

                    // Add into this section
                    if (item.extensions == null) item.extensions = new ArrayList<>();
                    if (!item.extensions.contains(ext)) {
                        item.extensions.add(ext);
                    }

                    notifyItemChanged(position);
                    saveNow();

                    Toast.makeText(context, ext + " mapped to " + item.name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ================================
    // REMOVE EXTENSION
    // ================================
    private void showRemoveExtensionDialog(int position) {
        CategoryModel item = categories.get(position);

        if (item.extensions == null || item.extensions.isEmpty()) {
            Toast.makeText(context, "No extensions to remove!", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] extArr = item.extensions.toArray(new String[0]);

        new AlertDialog.Builder(context)
                .setTitle("Remove Extension")
                .setItems(extArr, (dialog, which) -> {
                    String ext = extArr[which];

                    item.extensions.remove(ext);

                    notifyItemChanged(position);
                    saveNow();

                    Toast.makeText(context, ext + " removed from " + item.name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ================================
    // RENAME SECTION (custom only)
    // ================================
    private void showRenameDialog(int position) {
        CategoryModel item = categories.get(position);

        EditText input = new EditText(context);
        input.setHint("New name");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(item.name);

        new AlertDialog.Builder(context)
                .setTitle("Rename Section")
                .setView(input)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newName = input.getText().toString().trim();

                    if (newName.isEmpty()) {
                        Toast.makeText(context, "Name cannot be empty!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Prevent duplicates
                    for (CategoryModel c : categories) {
                        if (c != item && c.name.equalsIgnoreCase(newName)) {
                            Toast.makeText(context, "Section already exists!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    item.name = newName;
                    notifyItemChanged(position);
                    saveNow();

                    Toast.makeText(context, "Renamed successfully!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ================================
    // DELETE SECTION (custom only)
    // ================================
    private void showDeleteDialog(int position) {
        CategoryModel item = categories.get(position);

        new AlertDialog.Builder(context)
                .setTitle("Delete Section?")
                .setMessage("Delete \"" + item.name + "\" section?\nExtensions will be removed from mapping.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    categories.remove(position);
                    notifyItemRemoved(position);
                    saveNow();
                    Toast.makeText(context, "Deleted!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveNow() {
        if (listener != null) {
            listener.onUpdated(categories);
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;
        TextView extensions;
        ImageView btnEdit;

        public VH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.categoryIcon);
            title = itemView.findViewById(R.id.categoryTitle);
            extensions = itemView.findViewById(R.id.categoryExtensions);
            btnEdit = itemView.findViewById(R.id.btnEdit);
        }
    }
}
