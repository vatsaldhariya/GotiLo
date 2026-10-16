package com.gotilo.simtool.screen;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gotilo.simtool.R;
import com.gotilo.simtool.model.AppModel;

import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {

    private List<AppModel> appList;

    public AppListAdapter(List<AppModel> appList) {
        this.appList = appList;
    }

    // Allows the Search Bar to update the list dynamically
    public void filterList(List<AppModel> filteredList) {
        this.appList = filteredList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppModel app = appList.get(position);
        holder.tvAppName.setText(app.appName);
        holder.ivAppIcon.setImageDrawable(app.icon);

        // Clear previous listener to prevent recycling bugs
        holder.cbAppSelect.setOnCheckedChangeListener(null);
        holder.cbAppSelect.setChecked(app.isSelected);

        holder.cbAppSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            app.isSelected = isChecked;
        });

        // Make the whole row clickable
        holder.itemView.setOnClickListener(v -> holder.cbAppSelect.setChecked(!app.isSelected));
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;
        CheckBox cbAppSelect;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            cbAppSelect = itemView.findViewById(R.id.cbAppSelect);
        }
    }
}