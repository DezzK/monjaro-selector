/*
 * Copyright © 2026 Dezz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dezz.monjaro.drive_modes.ui.modes;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dezz.monjaro.drive_modes.R;
import dezz.monjaro.drive_modes.car.DriveModeCatalog;
import dezz.monjaro.drive_modes.car.DriveModeDescriptor;

/**
 * Adapter for the "active modes" list. Only enabled modes live here — the
 * user adds a mode by tapping a chip in the "available" section and removes
 * it via the trash button on the row.
 */
public class ModeListAdapter extends RecyclerView.Adapter<ModeListAdapter.Vh> {

    public interface Callback {
        void onRemove(int code);
        void onOrderChanged(List<Integer> newOrder);
        void onStartDrag(RecyclerView.ViewHolder vh);
    }

    private final List<Integer> codes = new ArrayList<>();
    private final Callback callback;

    public ModeListAdapter(@NonNull Callback callback) {
        this.callback = callback;
    }

    public void submit(@NonNull List<Integer> enabled) {
        codes.clear();
        codes.addAll(enabled);
        notifyDataSetChanged();
    }

    public void onItemMove(int from, int to) {
        if (from < 0 || to < 0 || from >= codes.size() || to >= codes.size()) return;
        Collections.swap(codes, from, to);
        notifyItemMoved(from, to);
    }

    public void commitOrder() {
        callback.onOrderChanged(new ArrayList<>(codes));
    }

    @NonNull
    @Override
    @SuppressLint("ClickableViewAccessibility")
    public Vh onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_drive_mode_row, parent, false);
        final Vh holder = new Vh(v);
        holder.removeButton.setOnClickListener(v2 -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            callback.onRemove(codes.get(pos));
        });
        holder.dragHandle.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                callback.onStartDrag(holder);
            }
            return false;
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Vh h, int position) {
        int code = codes.get(position);
        DriveModeDescriptor desc = DriveModeCatalog.byCodeOrGeneric(code);
        Context ctx = h.itemView.getContext();

        h.icon.setImageResource(desc.iconRes);
        h.icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, desc.accentRes)));
        h.label.setText(desc.labelRes);
    }

    @Override
    public int getItemCount() {
        return codes.size();
    }

    static class Vh extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final ImageView dragHandle;
        final MaterialButton removeButton;

        Vh(View v) {
            super(v);
            icon = v.findViewById(R.id.icon_mode);
            label = v.findViewById(R.id.label_mode);
            dragHandle = v.findViewById(R.id.drag_handle);
            removeButton = v.findViewById(R.id.btn_remove);
        }
    }
}
