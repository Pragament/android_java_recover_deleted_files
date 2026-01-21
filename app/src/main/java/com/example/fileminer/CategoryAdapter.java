package com.example.fileminer;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

    private final Context context;
    private final ArrayList<CategoryModel> list;

    public CategoryAdapter(Context context, ArrayList<CategoryModel> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_category_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CategoryModel item = list.get(position);

        holder.title.setText(item.name);
        holder.icon.setImageResource(item.iconRes);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, RestoredFilesActivity.class);
            intent.putExtra("sectionName", item.name);
            intent.putExtra("extensions", item.extensions); // pass list
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        ImageView icon;

        public VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.categoryTitle);
            icon = itemView.findViewById(R.id.categoryIcon);
        }
    }
}
