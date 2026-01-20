package com.example.fileminer;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.fileminer.databinding.ActivityManageCategoriesBinding;

import java.util.ArrayList;

public class ManageCategoriesActivity extends AppCompatActivity {

    private ActivityManageCategoriesBinding binding;

    private ArrayList<CategoryModel> categories;
    private ManageCategoryAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityManageCategoriesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        CategoryPrefsManager.initDefaultCategoriesIfNeeded(this);

        setSupportActionBar(binding.manageToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("File Extension Mapping");
        }

        categories = CategoryPrefsManager.getCategories(this);

        binding.recyclerViewCategories.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ManageCategoryAdapter(this, categories, updatedList -> {
            CategoryPrefsManager.saveCategories(this, updatedList);
        });

        binding.recyclerViewCategories.setAdapter(adapter);

        binding.fabAddCategory.setOnClickListener(v -> showAddDialog());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manage_categories_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        if (item.getItemId() == R.id.action_reset_default) {
            new AlertDialog.Builder(this)
                    .setTitle("Reset to Default?")
                    .setMessage("This will remove your custom sections and restore original mapping.")
                    .setPositiveButton("Reset", (d, w) -> {
                        CategoryPrefsManager.resetToDefault(this);
                        reloadList();
                        Toast.makeText(this, "Reset done!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void reloadList() {
        categories.clear();
        categories.addAll(CategoryPrefsManager.getCategories(this));
        adapter.notifyDataSetChanged();
    }

    private void showAddDialog() {
        EditText nameInput = new EditText(this);
        nameInput.setHint("Section Name (Example: Books)");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Create New Section")
                .setMessage("Enter section name")
                .setView(nameInput)
                .setPositiveButton("Next", (dialog, which) -> {

                    String sectionName = nameInput.getText().toString().trim();

                    if (sectionName.isEmpty()) {
                        Toast.makeText(this, "Section name cannot be empty!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    for (CategoryModel c : categories) {
                        if (c.name.equalsIgnoreCase(sectionName)) {
                            Toast.makeText(this, "Section already exists!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    showExtensionDialog(sectionName);

                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showExtensionDialog(String sectionName) {
        EditText extInput = new EditText(this);
        extInput.setHint("Extensions (Example: .pdf,.epub,.mobi)");
        extInput.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Extensions for " + sectionName)
                .setMessage("Enter extensions separated by comma")
                .setView(extInput)
                .setPositiveButton("Create", (dialog, which) -> {

                    String raw = extInput.getText().toString().trim();

                    if (raw.isEmpty()) {
                        Toast.makeText(this, "Extensions cannot be empty!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String[] parts = raw.split(",");
                    ArrayList<String> exts = new ArrayList<>();

                    for (String p : parts) {
                        String cleaned = CategoryPrefsManager.normalizeExtension(p);
                        if (!cleaned.isEmpty() && !exts.contains(cleaned)) {
                            exts.add(cleaned);
                        }
                    }

                    if (exts.isEmpty()) {
                        Toast.makeText(this, "Invalid extensions!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // ✅ Remove extensions from other sections (no duplicates allowed)
                    for (String ext : exts) {
                        CategoryPrefsManager.removeExtensionFromAllSections(categories, ext);
                    }

                    CategoryModel newCategory = new CategoryModel(sectionName, exts, R.drawable.ic_file, false);
                    categories.add(newCategory);

                    CategoryPrefsManager.saveCategories(this, categories);
                    adapter.notifyItemInserted(categories.size() - 1);

                    Toast.makeText(this, "Section created!", Toast.LENGTH_SHORT).show();

                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
