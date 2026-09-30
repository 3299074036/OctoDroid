package com.gh4a.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.R;
import com.gh4a.utils.DrawerManager;

import java.util.Collections;
import java.util.List;

public class DrawerEditAdapter extends RecyclerView.Adapter<DrawerEditAdapter.ViewHolder> {
    private final Context mContext;
    private final List<DrawerManager.DrawerItemDef> mItems;
    private ItemTouchHelper mTouchHelper;

    public DrawerEditAdapter(Context context, List<DrawerManager.DrawerItemDef> items) {
        mContext = context;
        mItems = items;
    }

    public void setTouchHelper(ItemTouchHelper helper) {
        mTouchHelper = helper;
    }

    public void moveItem(int from, int to) {
        if (from < 0 || to < 0 || from >= mItems.size() || to >= mItems.size()) {
            return;
        }
        Collections.swap(mItems, from, to);
        notifyItemMoved(from, to);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext)
                .inflate(R.layout.row_drawer_edit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DrawerManager.DrawerItemDef def = mItems.get(position);
        holder.title.setText(def.titleRes);
        holder.icon.setImageResource(def.iconRes);
        holder.visibilitySwitch.setOnCheckedChangeListener(null);
        holder.visibilitySwitch.setChecked(DrawerManager.isVisible(mContext, def.key));
        holder.visibilitySwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                DrawerManager.setVisible(mContext, def.key, isChecked));

        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && mTouchHelper != null) {
                mTouchHelper.startDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final SwitchCompat visibilitySwitch;
        final ImageView dragHandle;

        ViewHolder(View view) {
            super(view);
            icon = view.findViewById(R.id.icon);
            title = view.findViewById(R.id.title);
            visibilitySwitch = view.findViewById(R.id.visibility_switch);
            dragHandle = view.findViewById(R.id.drag_handle);
        }
    }
}
