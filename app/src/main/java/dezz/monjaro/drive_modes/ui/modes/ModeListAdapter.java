package dezz.monjaro.drive_modes.ui.modes;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dezz.monjaro.drive_modes.R;
import dezz.monjaro.drive_modes.car.DriveModeCatalog;
import dezz.monjaro.drive_modes.car.DriveModeDescriptor;
import dezz.monjaro.drive_modes.settings.ModeOrderEntry;

public class ModeListAdapter extends RecyclerView.Adapter<ModeListAdapter.Vh> {

    public interface Callback {
        void onEnabledChanged(int code, boolean enabled);
        void onOrderChanged(List<ModeOrderEntry> newOrder);
        void onStartDrag(RecyclerView.ViewHolder vh);
    }

    private final List<ModeOrderEntry> items = new ArrayList<>();
    private final Callback callback;

    public ModeListAdapter(@NonNull Callback callback) {
        this.callback = callback;
    }

    public void submit(@NonNull List<ModeOrderEntry> entries) {
        items.clear();
        items.addAll(entries);
        notifyDataSetChanged();
    }

    public void onItemMove(int from, int to) {
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size()) return;
        Collections.swap(items, from, to);
        notifyItemMoved(from, to);
    }

    public void commitOrder() {
        callback.onOrderChanged(new ArrayList<>(items));
    }

    @NonNull
    @Override
    public Vh onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_drive_mode_row, parent, false);
        return new Vh(v);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull Vh h, int position) {
        ModeOrderEntry entry = items.get(position);
        DriveModeDescriptor desc = DriveModeCatalog.byCodeOrGeneric(entry.code);
        Context ctx = h.itemView.getContext();

        h.icon.setImageResource(desc.iconRes);
        h.icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, desc.accentRes)));
        h.label.setText(desc.labelRes);

        h.checkbox.setOnCheckedChangeListener(null);
        h.checkbox.setChecked(entry.enabled);
        h.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            entry.enabled = isChecked;
            callback.onEnabledChanged(entry.code, isChecked);
        });

        h.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                callback.onStartDrag(h);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Vh extends RecyclerView.ViewHolder {
        final MaterialCheckBox checkbox;
        final FrameLayout iconHolder;
        final ImageView icon;
        final TextView label;
        final ImageView dragHandle;

        Vh(View v) {
            super(v);
            checkbox = v.findViewById(R.id.checkbox_enabled);
            icon = v.findViewById(R.id.icon_mode);
            iconHolder = (FrameLayout) icon.getParent();
            label = v.findViewById(R.id.label_mode);
            dragHandle = v.findViewById(R.id.drag_handle);
        }
    }
}
