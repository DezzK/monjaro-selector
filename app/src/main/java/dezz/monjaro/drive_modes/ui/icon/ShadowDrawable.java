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

package dezz.monjaro.drive_modes.ui.icon;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Wraps a delegate drawable and renders a soft drop shadow underneath. Used
 * to keep light-colored OEM icons visible on light backgrounds.
 *
 * Layout strategy: the wrapper reserves ~20% of its slot as outer margin and
 * draws the icon shrunk into the inner area. The shadow blur fits inside the
 * margin, so the parent ViewGroup never needs clipChildren=false. Callers
 * compensate for the size loss by sizing the ImageView/chip icon a bit larger
 * than the previous bare icon.
 */
public final class ShadowDrawable extends Drawable {

    /** Inner icon takes 80% of total bounds; the remaining 20% holds the shadow. */
    private static final int INNER_NUM = 8;
    private static final int INNER_DEN = 10;
    /** Blur radius as a fraction of the icon's drawn size. */
    private static final float BLUR_RATIO = 0.10f;
    /** Vertical shadow offset as a fraction of the icon's drawn size. */
    private static final float DY_RATIO = 0.03f;
    private static final int SHADOW_COLOR = 0x80000000;

    private final Drawable delegate;
    @Nullable private Bitmap shadowCache;
    private int cacheW = -1, cacheH = -1;

    public ShadowDrawable(@NonNull Drawable delegate) {
        // mutate() gives this wrapper its own tint/bounds state, so two
        // wrappers built from the same resource don't fight over tint.
        this.delegate = delegate.mutate();
    }

    @Override
    public int getIntrinsicWidth() {
        int iw = delegate.getIntrinsicWidth();
        return iw <= 0 ? iw : Math.round(iw * (float) INNER_DEN / INNER_NUM);
    }

    @Override
    public int getIntrinsicHeight() {
        int ih = delegate.getIntrinsicHeight();
        return ih <= 0 ? ih : Math.round(ih * (float) INNER_DEN / INNER_NUM);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        invalidateShadowCache();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        int bw = b.width();
        int bh = b.height();
        if (bw <= 0 || bh <= 0) return;

        int innerW = bw * INNER_NUM / INNER_DEN;
        int innerH = bh * INNER_NUM / INNER_DEN;
        int innerL = b.left + (bw - innerW) / 2;
        int innerT = b.top + (bh - innerH) / 2;

        if (shadowCache == null || cacheW != bw || cacheH != bh) {
            renderShadow(bw, bh, innerW, innerH);
        }
        if (shadowCache != null) {
            canvas.drawBitmap(shadowCache, b.left, b.top, null);
        }
        delegate.setBounds(innerL, innerT, innerL + innerW, innerT + innerH);
        delegate.draw(canvas);
    }

    private void renderShadow(int bw, int bh, int innerW, int innerH) {
        if (innerW <= 0 || innerH <= 0) return;
        Bitmap render = Bitmap.createBitmap(innerW, innerH, Bitmap.Config.ARGB_8888);
        Canvas rc = new Canvas(render);
        delegate.setBounds(0, 0, innerW, innerH);
        delegate.draw(rc);
        Bitmap alpha = render.extractAlpha();
        render.recycle();

        float blur = Math.max(0.5f, Math.min(innerW, innerH) * BLUR_RATIO);
        int dy = Math.round(Math.min(innerW, innerH) * DY_RATIO);

        Bitmap cache = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(cache);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
        p.setColor(SHADOW_COLOR);
        int innerL = (bw - innerW) / 2;
        int innerT = (bh - innerH) / 2;
        sc.drawBitmap(alpha, innerL, innerT + dy, p);
        alpha.recycle();

        if (shadowCache != null) shadowCache.recycle();
        shadowCache = cache;
        cacheW = bw;
        cacheH = bh;
    }

    private void invalidateShadowCache() {
        if (shadowCache != null) {
            shadowCache.recycle();
            shadowCache = null;
        }
        cacheW = -1;
        cacheH = -1;
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    @Override public void setAlpha(int alpha) {
        delegate.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(@Nullable ColorFilter cf) {
        delegate.setColorFilter(cf);
        invalidateSelf();
    }

    @Override public void setTintList(@Nullable ColorStateList tint) {
        delegate.setTintList(tint);
        invalidateSelf();
    }

    @Override public void setTintMode(@NonNull PorterDuff.Mode mode) {
        delegate.setTintMode(mode);
        invalidateSelf();
    }
}
