package dezz.monjaro.drive_modes.ui.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import dezz.monjaro.drive_modes.R;
import dezz.monjaro.drive_modes.car.DriveModeCatalog;
import dezz.monjaro.drive_modes.car.DriveModeDescriptor;

public class OverlayPillAdapter extends RecyclerView.Adapter<OverlayPillAdapter.PillVh> {

    public interface OnPillClickListener {
        void onPillClick(int modeCode, int position);
    }

    private final List<Integer> codes = new ArrayList<>();
    private int activeCode = -1;
    @Nullable
    private OnPillClickListener clickListener;

    public void setOnPillClickListener(@Nullable OnPillClickListener listener) {
        this.clickListener = listener;
    }

    public void setData(@NonNull List<Integer> newCodes, int activeCode) {
        codes.clear();
        codes.addAll(newCodes);
        this.activeCode = activeCode;
        notifyDataSetChanged();
    }

    public void setActive(int code) {
        if (activeCode == code) return;
        int prev = indexOf(activeCode);
        activeCode = code;
        int curr = indexOf(code);
        if (prev >= 0) notifyItemChanged(prev);
        if (curr >= 0) notifyItemChanged(curr);
    }

    public int indexOf(int code) {
        for (int i = 0; i < codes.size(); i++) {
            if (codes.get(i) == code) return i;
        }
        return -1;
    }

    public int getActiveCode() {
        return activeCode;
    }

    public List<Integer> getCodes() {
        return new ArrayList<>(codes);
    }

    @NonNull
    @Override
    public PillVh onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.overlay_mode_pill, parent, false);
        return new PillVh(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PillVh h, int position) {
        int code = codes.get(position);
        DriveModeDescriptor desc = DriveModeCatalog.byCodeOrGeneric(code);
        Context ctx = h.itemView.getContext();

        h.icon.setImageResource(desc.iconRes);
        h.label.setText(desc.labelRes);

        boolean active = (code == activeCode);
        int accent = ContextCompat.getColor(ctx, desc.accentRes);
        int onSurface = ContextCompat.getColor(ctx, R.color.m3_on_surface);
        int onSurfaceVariant = ContextCompat.getColor(ctx, R.color.m3_on_surface_variant);

        if (active) {
            h.iconHolder.setBackgroundResource(R.drawable.bg_pill_active);
            h.icon.setImageTintList(ColorStateList.valueOf(accent));
            h.label.setTextColor(onSurface);
            h.label.setAlpha(1f);
            animateScale(h.itemView, 1.05f);
        } else {
            h.iconHolder.setBackgroundResource(R.drawable.bg_pill_inactive);
            h.icon.setImageTintList(ColorStateList.valueOf(onSurfaceVariant));
            h.label.setTextColor(onSurfaceVariant);
            h.label.setAlpha(0.6f);
            animateScale(h.itemView, 0.92f);
        }

        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPillClick(code, position);
            }
        });
    }

    private void animateScale(View v, float to) {
        if (Math.abs(v.getScaleX() - to) < 0.005f) {
            v.setScaleX(to);
            v.setScaleY(to);
            return;
        }
        float from = v.getScaleX() == 0f ? 1f : v.getScaleX();
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(220);
        a.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            v.setScaleX(val);
            v.setScaleY(val);
        });
        a.start();
    }

    @Override
    public int getItemCount() {
        return codes.size();
    }

    static class PillVh extends RecyclerView.ViewHolder {
        final FrameLayout iconHolder;
        final ImageView icon;
        final TextView label;

        PillVh(View v) {
            super(v);
            iconHolder = v.findViewById(R.id.pill_icon_holder);
            icon = v.findViewById(R.id.pill_icon);
            label = v.findViewById(R.id.pill_label);
        }
    }
}
