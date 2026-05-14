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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import java.util.ArrayList;
import java.util.List;

import dezz.monjaro.drive_modes.R;
import dezz.monjaro.drive_modes.knob.KnobIntents;
import dezz.monjaro.drive_modes.util.Logs;

/**
 * Owns the WindowManager overlay: creation, updates, fade-in/out, auto-hide.
 *
 * Key behaviours:
 *  - Card width is sized to fit pill count, capped at {@link #MAX_WIDTH_FRACTION}
 *    of the screen.
 *  - Static mode: scrolling stops at list ends (no empty padding on edges).
 *  - Carousel mode: the real list is repeated {@link #CAROUSEL_REPEATS} times
 *    into a virtual strip. The active CODE is highlighted — so every copy of
 *    the selected mode is lit, and free scrolling never changes the selection.
 *    Switching the active mode (tap or programmatic show with a new code)
 *    triggers a SMOOTH scroll to the nearest copy of the new code.
 *  - Free scrolling does NOT change the selected mode. To change selection
 *    the user must tap a pill.
 *  - User activity inside the recycler restarts auto-hide.
 *  - {@link #animateStepsTo} visually walks through intermediate modes for
 *    multi-click series, even though the actual mode is already set to the
 *    final value.
 */
public class OverlayController {

    public interface OnModeTapListener {
        @MainThread
        void onModeTapped(int modeCode);
    }

    private static final long FADE_IN_MS = 200L;
    private static final long FADE_OUT_MS = 280L;
    private static final long INTERMEDIATE_STEP_MS = 140L;
    private static final float MAX_WIDTH_FRACTION = 0.8f;
    /** Minimum gap between consecutive scheduleAutoHide() reschedules. Without
     *  this, a single fling triggers hundreds of removeCallbacks/postDelayed
     *  pairs per second. */
    private static final long AUTO_HIDE_RESCHEDULE_MIN_MS = 100L;

    /**
     * How many times the real list is repeated to form the carousel strip.
     * Must be odd so a real list copy can sit centered. With 11 copies and
     * a max real count of 24, the strip is at most ~264 items — well within
     * RecyclerView's comfort zone, and gives plenty of room before edges are
     * reached and a silent rebase is needed.
     */
    private static final int CAROUSEL_REPEATS = 11;

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OverlayPillAdapter adapter = new OverlayPillAdapter();

    private final Runnable hideRunnable = this::hide;
    private final List<Runnable> queuedSteps = new ArrayList<>();

    private View root;
    private View overlayCard;
    private RecyclerView recycler;
    @Nullable
    private SnapHelper snapHelper;
    /** Auto-hide duration for the CURRENT show. Recomputed on every show call. */
    private int currentAutoHideMs = 3000;
    private boolean attached;
    private boolean hiding;
    private boolean carouselMode;

    /**
     * Snapshot of the last real list passed to {@link #show}. Used to decide
     * whether the existing virtual strip can be reused for a smooth transition
     * or needs to be rebuilt from scratch.
     */
    @Nullable
    private List<Integer> currentRealCodes;

    @Nullable
    private ViewTreeObserver.OnGlobalLayoutListener pendingLayoutListener;

    /** Cached card-width inputs in pixels. -1 = not yet computed. */
    private int cachedPillFootprintPx = -1;
    private int cachedSidePaddingPx = -1;
    private int cachedMaxWidthPx = -1;

    /** Time of last scheduleAutoHide() reschedule (elapsedRealtime). */
    private long lastAutoHideScheduleAt;

    /** Tracks the last value emitted via {@link #broadcastVisibility} to dedup
     *  repeated show() calls — MConfig+ only needs edge transitions. */
    private boolean lastVisibilityBroadcast;

    @Nullable
    private OnModeTapListener tapListener;

    public OverlayController(@NonNull Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void setOnModeTapListener(@Nullable OnModeTapListener listener) {
        this.tapListener = listener;
        adapter.setOnPillClickListener((modeCode, position) -> handlePillClick(modeCode, position));
    }

    /**
     * Tap on a pill: that pill becomes the new active one. We immediately
     * update the active code, smooth-scroll the tapped position into the
     * center, and notify the service. The subsequent programmatic
     * {@link #show} that arrives after the SDK round-trip will see the same
     * active code and skip any further scroll.
     */
    private void handlePillClick(int modeCode, int position) {
        if (position >= 0) {
            adapter.setActiveCode(modeCode);
            scrollToCenterIndex(position, /* animate */ true);
        }
        if (tapListener != null) tapListener.onModeTapped(modeCode);
    }

    /**
     * In carousel mode the list is rendered as a virtual strip (repeated real
     * list) and every copy of the active code is highlighted; mode changes
     * smooth-scroll to the nearest copy. In static mode the real list is
     * rendered once and scrolling stops at the ends.
     */
    public void setCarouselMode(boolean enabled) {
        if (this.carouselMode == enabled) return;
        this.carouselMode = enabled;
        // Force a rebuild on the next show so the layout reflects the new mode.
        currentRealCodes = null;
    }

    @MainThread
    public void show(@NonNull List<Integer> orderedCodes, int activeCode, int autoHideMs) {
        if (orderedCodes.isEmpty()) {
            Logs.d("Overlay show skipped — empty mode list");
            return;
        }
        ensureAttached();
        if (root == null) return;

        currentAutoHideMs = autoHideMs;
        cancelQueuedSteps();

        if (carouselMode) {
            showCarousel(orderedCodes, activeCode);
        } else {
            showStatic(orderedCodes, activeCode);
        }

        appearIfNeeded();
        broadcastVisibility(true);
        scheduleAutoHide(/* force */ true);
    }

    /**
     * Show the overlay from the starting active code and then smoothly walk
     * through the intermediate codes to the final one. Each step takes
     * {@link #INTERMEDIATE_STEP_MS} ms. Auto-hide is scheduled after the last step.
     */
    @MainThread
    public void animateStepsTo(@NonNull List<Integer> orderedCodes,
                               int startCode,
                               @NonNull List<Integer> stepsToHighlight,
                               int autoHideMs) {
        if (orderedCodes.isEmpty() || stepsToHighlight.isEmpty()) {
            if (!orderedCodes.isEmpty()) show(orderedCodes, startCode, autoHideMs);
            return;
        }
        ensureAttached();
        if (root == null) return;

        currentAutoHideMs = autoHideMs;
        cancelQueuedSteps();

        if (carouselMode) {
            showCarousel(orderedCodes, startCode);
        } else {
            showStatic(orderedCodes, startCode);
        }

        long delay = INTERMEDIATE_STEP_MS;
        for (int i = 0; i < stepsToHighlight.size(); i++) {
            final int code = stepsToHighlight.get(i);
            final List<Integer> codesRef = orderedCodes;
            Runnable step = () -> {
                if (carouselMode) {
                    showCarousel(codesRef, code);
                } else {
                    showStatic(codesRef, code);
                }
            };
            queuedSteps.add(step);
            handler.postDelayed(step, delay);
            delay += INTERMEDIATE_STEP_MS;
        }
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, delay + autoHideMs);

        appearIfNeeded();
        broadcastVisibility(true);
    }

    @MainThread
    public void hide() {
        if (root == null || !attached || hiding) return;
        hiding = true;
        cancelQueuedSteps();
        handler.removeCallbacks(hideRunnable);
        root.animate().cancel();
        root.animate()
                .alpha(0f)
                .translationY(8f)
                .setDuration(FADE_OUT_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    hiding = false;
                    if (root != null) root.setVisibility(View.GONE);
                    broadcastVisibility(false);
                })
                .start();
    }

    @MainThread
    public void dispose() {
        handler.removeCallbacks(hideRunnable);
        cancelQueuedSteps();
        removePendingLayoutListener();
        if (root != null && attached) {
            try {
                windowManager.removeView(root);
            } catch (IllegalArgumentException ignored) {
            }
        }
        // If the overlay was still visible when teardown started, MConfig+
        // would otherwise be left thinking it is showing forever.
        broadcastVisibility(false);
        attached = false;
        root = null;
        overlayCard = null;
        recycler = null;
        snapHelper = null;
        currentRealCodes = null;
    }

    // ------------------------------------------------------------------
    // Static (non-carousel) path
    // ------------------------------------------------------------------

    private void showStatic(@NonNull List<Integer> realCodes, int activeCode) {
        adjustCardWidth(realCodes.size());

        boolean codesSame = currentRealCodes != null
                && currentRealCodes.equals(realCodes)
                && adapter.getItemCount() == realCodes.size();

        int activePos = realCodes.indexOf(activeCode);
        if (activePos < 0) activePos = 0;

        if (codesSame) {
            adapter.setActiveCode(activeCode);
            scrollToCenterIndex(activePos, /* animate */ isCurrentlyVisible());
        } else {
            adapter.setData(new ArrayList<>(realCodes), activeCode);
            currentRealCodes = new ArrayList<>(realCodes);
            scrollToCenterIndex(activePos, /* animate */ false);
        }
    }

    // ------------------------------------------------------------------
    // Carousel path
    // ------------------------------------------------------------------

    private void showCarousel(@NonNull List<Integer> realCodes, int requestedCode) {
        int realCount = realCodes.size();
        int realIdx = Math.max(0, realCodes.indexOf(requestedCode));
        adjustCardWidth(realCount);

        boolean stripReady = currentRealCodes != null
                && currentRealCodes.equals(realCodes)
                && adapter.getItemCount() == realCount * CAROUSEL_REPEATS;

        if (!stripReady) {
            rebuildCarousel(realCodes, realIdx);
            return;
        }

        if (requestedCode == adapter.getActiveCode()) {
            // Same selection — do not yank the viewport from wherever the
            // user has scrolled to.
            return;
        }

        // Different code requested. Pick the copy nearest to the current
        // viewport center, so the smooth scroll travels the minimum distance.
        int basePos = findSnapPosition();
        if (basePos < 0) basePos = realCount * (CAROUSEL_REPEATS / 2);
        int baseRealIdx = ((basePos % realCount) + realCount) % realCount;
        int diff = wrapDiff(baseRealIdx, realIdx, realCount);
        int target = basePos + diff;

        if (needsRebase(target, realCount)) {
            rebuildCarousel(realCodes, realIdx);
        } else {
            adapter.setActiveCode(requestedCode);
            scrollToCenterIndex(target, /* animate */ true);
        }
    }

    private void rebuildCarousel(@NonNull List<Integer> realCodes, int realIdx) {
        int realCount = realCodes.size();
        int total = realCount * CAROUSEL_REPEATS;
        List<Integer> display = new ArrayList<>(total);
        for (int i = 0; i < total; i++) display.add(realCodes.get(i % realCount));
        int center = realCount * (CAROUSEL_REPEATS / 2) + realIdx;
        adapter.setData(display, realCodes.get(realIdx));
        currentRealCodes = new ArrayList<>(realCodes);
        // No animation on rebuild — the underlying data just changed wholesale.
        scrollToCenterIndex(center, /* animate */ false);
    }

    /**
     * Shortest signed difference from {@code from} to {@code to} on a ring of
     * size {@code n}. Range: [-n/2, n/2].
     */
    static int wrapDiff(int from, int to, int n) {
        if (n <= 0) return 0;
        int diff = ((to - from) % n + n) % n;
        if (diff > n / 2) diff -= n;
        return diff;
    }

    /**
     * True if the candidate position is too close to either end of the virtual
     * strip and a silent rebase to the center is warranted.
     */
    private boolean needsRebase(int virtualPos, int realCount) {
        if (realCount <= 0) return false;
        int total = realCount * CAROUSEL_REPEATS;
        return virtualPos < realCount || virtualPos >= total - realCount;
    }

    private boolean isCurrentlyVisible() {
        return root != null
                && attached
                && !hiding
                && root.getVisibility() == View.VISIBLE
                && root.getAlpha() > 0.01f;
    }

    // ------------------------------------------------------------------
    // Window / view setup
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private void ensureAttached() {
        if (root != null && attached) return;
        root = LayoutInflater.from(context).inflate(R.layout.overlay_root, null);
        overlayCard = root.findViewById(R.id.overlay_card);
        recycler = root.findViewById(R.id.overlay_recycler);

        LinearLayoutManager lm = new LinearLayoutManager(
                context, LinearLayoutManager.HORIZONTAL, false);
        recycler.setLayoutManager(lm);
        recycler.setAdapter(adapter);
        // Disable item animator: we drive scale/alpha ourselves via
        // ViewPropertyAnimator in the adapter. The default change-animation
        // wraps the visible pill in a temporary transition during which our
        // scaleX/scaleY animation doesn't visibly take effect — so the pill
        // appeared to stay small until it got recycled off-screen.
        recycler.setItemAnimator(null);
        snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(recycler);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dx != 0 || dy != 0) scheduleAutoHide();
            }
        });
        recycler.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv,
                                                 @NonNull MotionEvent e) {
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN
                        || action == MotionEvent.ACTION_MOVE
                        || action == MotionEvent.ACTION_POINTER_DOWN) {
                    scheduleAutoHide();
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {}

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });

        root.setClickable(true);
        root.setOnClickListener(v -> hide());

        root.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                hide();
                return true;
            }
            return false;
        });

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;

        root.setVisibility(View.INVISIBLE);
        root.setAlpha(0f);
        try {
            windowManager.addView(root, lp);
            attached = true;
        } catch (Throwable t) {
            Logs.w("addView failed: " + t.getMessage());
            root = null;
            overlayCard = null;
            recycler = null;
            attached = false;
        }
    }

    private void appearIfNeeded() {
        if (root == null) return;
        hiding = false;
        if (root.getVisibility() != View.VISIBLE || root.getAlpha() < 0.99f) {
            root.setVisibility(View.VISIBLE);
            root.setTranslationY(12f);
            root.setAlpha(0f);
            root.animate().cancel();
            root.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(FADE_IN_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(null)
                    .start();
        }
    }

    /**
     * Card width = N pills + recycler padding, capped at
     * {@link #MAX_WIDTH_FRACTION} of the screen. Sized off the REAL pill count
     * regardless of carousel virtual strip length.
     */
    private void adjustCardWidth(int realCount) {
        if (overlayCard == null) return;
        ensureWidthMetricsCached();
        int desired = cachedSidePaddingPx * 2 + cachedPillFootprintPx * realCount;
        int width = Math.min(desired, cachedMaxWidthPx);
        ViewGroup.LayoutParams lp = overlayCard.getLayoutParams();
        if (lp.width != width) {
            lp.width = width;
            overlayCard.setLayoutParams(lp);
        }
    }

    private void ensureWidthMetricsCached() {
        if (cachedPillFootprintPx > 0) return;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        cachedMaxWidthPx = (int) (dm.widthPixels * MAX_WIDTH_FRACTION);
        int minWidthPx = (int) context.getResources().getDimension(R.dimen.overlay_pill_min_width);
        int spacingPx = (int) context.getResources().getDimension(R.dimen.overlay_item_spacing);
        cachedPillFootprintPx = minWidthPx + spacingPx * 2;
        cachedSidePaddingPx = (int) context.getResources().getDimension(R.dimen.overlay_padding_horizontal);
    }

    // ------------------------------------------------------------------
    // Scrolling
    // ------------------------------------------------------------------

    /**
     * Bring {@code position} to the horizontal center of the viewport.
     * If {@code animate} is true, uses a {@link LinearSmoothScroller}. Otherwise
     * uses {@link LinearLayoutManager#scrollToPositionWithOffset} for an
     * instant jump — used for rebuilds where the data just changed wholesale.
     */
    private void scrollToCenterIndex(int position, boolean animate) {
        if (recycler == null || position < 0) return;
        if (!isRecyclerLaidOut()) {
            // The recycler hasn't laid out children yet. Park a listener that
            // fires once first layout completes — we keep a reference so
            // dispose() can detach it explicitly if the overlay is torn down
            // before layout ever happens.
            removePendingLayoutListener();
            pendingLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (recycler == null) return;
                    recycler.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    pendingLayoutListener = null;
                    performScrollToCenter(position, animate);
                }
            };
            recycler.getViewTreeObserver().addOnGlobalLayoutListener(pendingLayoutListener);
        } else {
            recycler.post(() -> performScrollToCenter(position, animate));
        }
    }

    private boolean isRecyclerLaidOut() {
        if (recycler == null) return false;
        if (recycler.getChildCount() == 0 || recycler.getWidth() == 0) return false;
        View first = recycler.getChildAt(0);
        return first != null && first.getWidth() > 0;
    }

    private void removePendingLayoutListener() {
        if (pendingLayoutListener != null && recycler != null) {
            recycler.getViewTreeObserver().removeOnGlobalLayoutListener(pendingLayoutListener);
        }
        pendingLayoutListener = null;
    }

    private void performScrollToCenter(int position, boolean animate) {
        if (recycler == null) return;
        RecyclerView.LayoutManager lm = recycler.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return;
        LinearLayoutManager llm = (LinearLayoutManager) lm;

        if (animate) {
            LinearSmoothScroller scroller = new LinearSmoothScroller(recycler.getContext()) {
                @Override
                public int calculateDxToMakeVisible(View view, int snapPreference) {
                    RecyclerView.LayoutManager mgr = getLayoutManager();
                    if (mgr == null || !mgr.canScrollHorizontally()) return 0;
                    int left = mgr.getDecoratedLeft(view);
                    int right = mgr.getDecoratedRight(view);
                    int childCenter = (left + right) / 2;
                    int containerCenter = mgr.getWidth() / 2;
                    return containerCenter - childCenter;
                }
            };
            scroller.setTargetPosition(position);
            llm.startSmoothScroll(scroller);
        } else {
            // The recycler may have been emptied between the post() and now.
            View first = recycler.getChildAt(0);
            if (first == null || first.getWidth() <= 0) return;
            int pillWidth = first.getWidth();
            int offset = (recycler.getWidth() - pillWidth) / 2;
            llm.scrollToPositionWithOffset(position, offset);
        }
    }

    private int findSnapPosition() {
        if (snapHelper == null || recycler == null) return RecyclerView.NO_POSITION;
        RecyclerView.LayoutManager lm = recycler.getLayoutManager();
        if (lm == null) return RecyclerView.NO_POSITION;
        View v = snapHelper.findSnapView(lm);
        if (v == null) return RecyclerView.NO_POSITION;
        return lm.getPosition(v);
    }

    /**
     * Broadcast the current overlay visibility so MConfig+ (v43+) can route
     * the same physical click as either "show overlay" or "step mode"
     * depending on whether the overlay is already on screen.
     *
     * Deduped on state transitions: repeat show() calls (or a tap on a pill
     * that calls show() again) do not spam the receiver.
     */
    private void broadcastVisibility(boolean isShowing) {
        if (lastVisibilityBroadcast == isShowing) return;
        lastVisibilityBroadcast = isShowing;
        try {
            Intent intent = new Intent(KnobIntents.ACTION_OVERLAY_VISIBILITY);
            intent.putExtra(KnobIntents.EXTRA_IS_SHOWING, isShowing);
            // FLAG_INCLUDE_STOPPED_PACKAGES + FLAG_RECEIVER_INCLUDE_BACKGROUND
            // (0x01000000) so the broadcast reaches MConfig+ even if it has
            // been stopped or is in background-restricted state.
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | 0x01000000);
            context.sendBroadcast(intent);
        } catch (Throwable t) {
            Logs.w("Visibility broadcast failed: " + t.getMessage());
        }
    }

    private void scheduleAutoHide() {
        scheduleAutoHide(/* force */ false);
    }

    private void scheduleAutoHide(boolean force) {
        long now = android.os.SystemClock.uptimeMillis();
        // Throttle: during a fling onScrolled fires every frame and the
        // pending hide already covers any practical window — no need to
        // tear it down and rebuild it dozens of times a second. Explicit
        // shows pass force=true so they always (re)arm the timer.
        if (!force && now - lastAutoHideScheduleAt < AUTO_HIDE_RESCHEDULE_MIN_MS) return;
        lastAutoHideScheduleAt = now;
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, currentAutoHideMs);
    }

    private void cancelQueuedSteps() {
        for (Runnable r : queuedSteps) {
            handler.removeCallbacks(r);
        }
        queuedSteps.clear();
    }
}
