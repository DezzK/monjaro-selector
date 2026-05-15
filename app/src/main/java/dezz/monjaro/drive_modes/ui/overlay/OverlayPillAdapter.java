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
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
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
 *
 * Crisp rendering at both rest states: each pill has two layout sizes — large
 * (active) and small (inactive). At rest the views actually use the matching
 * layout dimensions, so VectorDrawables rasterize once at the displayed pixel
 * count and look sharp. During the active/inactive transition we snap layout
 * to the active dimensions and animate scaleX/scaleY between 1.0 and
 * SCALE_INACTIVE_FROM_ACTIVE — motion masks any bitmap interpolation, and
 * when the animation lands we snap layout to the target rest size.
 */
public class OverlayPillAdapter extends RecyclerView.Adapter<OverlayPillAdapter.PillVh> {

    public interface OnPillClickListener {
        void onPillClick(int modeCode, int position);
    }

    public static final int NO_CODE = Integer.MIN_VALUE;

    /** Visual ratio of an inactive pill versus an active one. Must match the
     *  ratio of the *_small dimens to their active counterparts so the layout
     *  snap at animation end has no visible jump. */
    private static final float SCALE_INACTIVE_FROM_ACTIVE = 0.71f;
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

        // Tint policy is the same for active and inactive: pre-colored OEM
        // icons stay as designed, plain silhouettes get the accent.
        h.icon.setImageTintList(desc.iconIsColored ? null : ColorStateList.valueOf(accent));
        h.icon.setAlpha(1f);

        if (active) {
            h.iconHolder.setBackgroundResource(R.drawable.bg_pill_active);
            h.label.setTextColor(onSurface);
            h.label.setAlpha(1f);
        } else {
            h.iconHolder.setBackgroundResource(R.drawable.bg_pill_inactive);
            h.label.setTextColor(onSurfaceVariant);
            h.label.setAlpha(0.7f);
        }

        // First time this VH sees the code (initial layout or post-recycle):
        // snap to the target rest size with no animation. State change for an
        // already-bound code: run the scale animation between the two sizes.
        boolean firstBindForThisCode = (h.boundCode != code);
        h.boundCode = code;
        if (firstBindForThisCode) {
            h.applyRest(active);
        } else {
            h.animateTo(active);
        }
    }

    @Override
    public void onViewRecycled(@NonNull PillVh h) {
        super.onViewRecycled(h);
        h.itemView.animate().cancel();
        // Reset so the next binding (likely a different mode) snaps without
        // animation rather than animating from whatever state was last shown.
        h.boundCode = NO_CODE;
    }

    @Override
    public int getItemCount() {
        return codes.size();
    }

    static class PillVh extends RecyclerView.ViewHolder {
        final FrameLayout iconHolder;
        final ImageView icon;
        final TextView label;
        final int holderActivePx;
        final int holderInactivePx;
        final int iconActivePx;
        final int iconInactivePx;
        final float labelActivePx;
        final float labelInactivePx;
        int boundCode = NO_CODE;
        boolean atRestActive = true;

        PillVh(View v) {
            super(v);
            iconHolder = v.findViewById(R.id.pill_icon_holder);
            icon = v.findViewById(R.id.pill_icon);
            label = v.findViewById(R.id.pill_label);
            Resources r = v.getResources();
            holderActivePx = r.getDimensionPixelSize(R.dimen.overlay_pill_icon);
            holderInactivePx = r.getDimensionPixelSize(R.dimen.overlay_pill_icon_small);
            iconActivePx = r.getDimensionPixelSize(R.dimen.overlay_pill_icon_image);
            iconInactivePx = r.getDimensionPixelSize(R.dimen.overlay_pill_icon_image_small);
            labelActivePx = r.getDimension(R.dimen.overlay_pill_label);
            labelInactivePx = r.getDimension(R.dimen.overlay_pill_label_small);
        }

        /** Apply the target state's layout dims with no animation. */
        void applyRest(boolean active) {
            itemView.animate().cancel();
            applyLayoutDims(active);
            itemView.setScaleX(1f);
            itemView.setScaleY(1f);
            atRestActive = active;
        }

        /**
         * Animate from the current rest state to {@code targetActive}. During
         * the animation the views actually sit at active-size and we scale
         * down via View.scaleX/Y; the layout snaps to the target rest size
         * once the animator finishes.
         */
        void animateTo(boolean targetActive) {
            if (atRestActive == targetActive) {
                // No state change. Make sure we're snapped cleanly in case
                // the previous animation was interrupted.
                applyRest(targetActive);
                return;
            }
            itemView.animate().cancel();

            // If we're currently at inactive layout (small views), grow the
            // layout to active dims so the animation has the larger raster
            // to bitmap-scale; counter-scale so the visible size doesn't
            // jump at the start of the animation.
            if (!atRestActive) {
                applyLayoutDims(true);
                itemView.setScaleX(SCALE_INACTIVE_FROM_ACTIVE);
                itemView.setScaleY(SCALE_INACTIVE_FROM_ACTIVE);
            }
            final boolean target = targetActive;
            final float endScale = targetActive ? 1f : SCALE_INACTIVE_FROM_ACTIVE;
            atRestActive = targetActive;
            itemView.animate()
                    .scaleX(endScale)
                    .scaleY(endScale)
                    .setDuration(SCALE_ANIM_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> applyRest(target))
                    .start();
        }

        private void applyLayoutDims(boolean active) {
            int holderSize = active ? holderActivePx : holderInactivePx;
            ViewGroup.LayoutParams hlp = iconHolder.getLayoutParams();
            if (hlp.width != holderSize || hlp.height != holderSize) {
                hlp.width = holderSize;
                hlp.height = holderSize;
                iconHolder.setLayoutParams(hlp);
            }
            int iconSize = active ? iconActivePx : iconInactivePx;
            ViewGroup.LayoutParams ilp = icon.getLayoutParams();
            if (ilp.width != iconSize || ilp.height != iconSize) {
                ilp.width = iconSize;
                ilp.height = iconSize;
                icon.setLayoutParams(ilp);
            }
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    active ? labelActivePx : labelInactivePx);
        }
    }
}
