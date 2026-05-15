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

package dezz.monjaro.drive_modes.ui.overlay;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
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
import dezz.monjaro.drive_modes.ui.icon.ShadowDrawable;

/**
 * Highlight is driven by the active CODE: in carousel mode every copy of the
 * selected mode is highlighted, so when the user scrolls and one copy moves
 * off-screen the next copy entering the viewport is already lit. The selection
 * only changes via explicit tap or a programmatic show — never via scrolling.
 */
public class OverlayPillAdapter extends RecyclerView.Adapter<OverlayPillAdapter.PillVh> {

    public interface OnPillClickListener {
        void onPillClick(int modeCode, int position);
    }

    public static final int NO_CODE = Integer.MIN_VALUE;

    private static final float SCALE_ACTIVE = 1.10f;
    private static final float SCALE_INACTIVE = 0.78f;
    private static final long SCALE_ANIM_MS = 260L;

    private final List<Integer> codes = new ArrayList<>();
    private int activeCode = NO_CODE;
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

    /**
     * Change the active code. Only the items whose code is the OLD or NEW
     * value are rebound, so this scales cheaply even with the long carousel
     * strip.
     */
    public void setActiveCode(int newCode) {
        if (newCode == activeCode) return;
        int prev = activeCode;
        activeCode = newCode;
        for (int i = 0; i < codes.size(); i++) {
            int c = codes.get(i);
            if (c == prev || c == newCode) notifyItemChanged(i);
        }
    }

    public int getActiveCode() {
        return activeCode;
    }

    public int getCodeAt(int position) {
        if (position < 0 || position >= codes.size()) return NO_CODE;
        return codes.get(position);
    }

    @NonNull
    @Override
    public PillVh onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.overlay_mode_pill, parent, false);
        final PillVh holder = new PillVh(v);
        // Bind the click listener once: it looks up the current code via the
        // bound adapter position rather than capturing a stale value.
        v.setOnClickListener(view -> {
            if (clickListener == null) return;
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || pos >= codes.size()) return;
            clickListener.onPillClick(codes.get(pos), pos);
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull PillVh h, int position) {
        int code = codes.get(position);
        DriveModeDescriptor desc = DriveModeCatalog.byCodeOrGeneric(code);
        Context ctx = h.itemView.getContext();

        Drawable raw = ContextCompat.getDrawable(ctx, desc.iconRes);
        h.icon.setImageDrawable(raw != null ? new ShadowDrawable(raw) : null);
        h.label.setText(desc.labelRes);

        boolean active = (code == activeCode);
        int accent = ContextCompat.getColor(ctx, desc.accentRes);
        int onSurface = ContextCompat.getColor(ctx, R.color.m3_on_surface);
        int onSurfaceVariant = ContextCompat.getColor(ctx, R.color.m3_on_surface_variant);

        if (active) {
            h.iconHolder.setBackgroundResource(R.drawable.bg_pill_active);
            // Keep OEM colors on active; only tint plain silhouettes with accent.
            h.icon.setImageTintList(desc.iconIsColored ? null : ColorStateList.valueOf(accent));
            h.label.setTextColor(onSurface);
            h.label.setAlpha(1f);
            animateScale(h.itemView, SCALE_ACTIVE);
        } else {
            h.iconHolder.setBackgroundResource(R.drawable.bg_pill_inactive);
            // Inactive: desaturate every icon, OEM-colored or not, so the active
            // item stands out.
            h.icon.setImageTintList(ColorStateList.valueOf(onSurfaceVariant));
            h.label.setTextColor(onSurfaceVariant);
            h.label.setAlpha(0.55f);
            animateScale(h.itemView, SCALE_INACTIVE);
        }
    }

    @Override
    public void onViewRecycled(@NonNull PillVh h) {
        super.onViewRecycled(h);
        h.itemView.animate().cancel();
    }

    private void animateScale(View v, float to) {
        if (Math.abs(v.getScaleX() - to) < 0.005f) {
            v.setScaleX(to);
            v.setScaleY(to);
            return;
        }
        v.animate().cancel();
        v.animate()
                .scaleX(to)
                .scaleY(to)
                .setDuration(SCALE_ANIM_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
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
