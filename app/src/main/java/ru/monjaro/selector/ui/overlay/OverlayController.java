package ru.monjaro.selector.ui.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
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

import ru.monjaro.selector.R;
import ru.monjaro.selector.util.Logs;

/**
 * Управляет WindowManager-оверлеем: создание, обновление, fade-in/out, auto-hide.
 *
 * Ключевые гарантии:
 *  - autoHide использует токен, поэтому отмена точечная и не задевает другие postDelayed.
 *  - fade-in/out через ViewPropertyAnimator (надёжнее legacy Animation API).
 *  - При повторных show() в течение autoHide-окна таймер сбрасывается.
 *  - Любая активность пользователя внутри recycler (touch/scroll) перезапускает auto-hide.
 *  - {@link #animateStepsTo} визуально проходит через промежуточные режимы для серий
 *    кликов, хотя фактически режим уже установлен на финальное значение.
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

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OverlayPillAdapter adapter = new OverlayPillAdapter();

    private final Runnable hideRunnable = this::hide;
    private final List<Runnable> queuedSteps = new ArrayList<>();

    private View root;
    private View overlayCard;
    private RecyclerView recycler;
    /** Длительность auto-hide ТЕКУЩЕГО показа. Перерасчитывается при каждом show. */
    private int currentAutoHideMs = 3000;
    private boolean attached;
    private boolean hiding;

    @Nullable
    private OnModeTapListener tapListener;

    public OverlayController(@NonNull Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void setOnModeTapListener(@Nullable OnModeTapListener listener) {
        this.tapListener = listener;
        adapter.setOnPillClickListener(listener == null
                ? null
                : (modeCode, position) -> listener.onModeTapped(modeCode));
    }

    @MainThread
    public void show(@NonNull List<Integer> orderedCodes, int activeCode, int autoHideMs) {
        if (orderedCodes.isEmpty()) {
            Logs.d("Overlay show skipped — пустой список режимов");
            return;
        }
        ensureAttached();
        if (root == null) return;

        currentAutoHideMs = autoHideMs;
        cancelQueuedSteps();
        adapter.setData(new ArrayList<>(orderedCodes), activeCode);
        scrollToActive(activeCode);
        appearIfNeeded();
        scheduleAutoHide();
    }

    /**
     * Показать оверлей со стартового активного и затем плавно пройти через
     * промежуточные коды к финальному. Каждый шаг — {@link #INTERMEDIATE_STEP_MS} мс.
     * Авто-hide планируется после завершения всех шагов.
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
        adapter.setData(new ArrayList<>(orderedCodes), startCode);
        scrollToActive(startCode);
        appearIfNeeded();

        long delay = INTERMEDIATE_STEP_MS;
        for (int i = 0; i < stepsToHighlight.size(); i++) {
            final int code = stepsToHighlight.get(i);
            Runnable step = () -> {
                adapter.setActive(code);
                scrollToActive(code);
            };
            queuedSteps.add(step);
            handler.postDelayed(step, delay);
            delay += INTERMEDIATE_STEP_MS;
        }
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, delay + autoHideMs);
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
                })
                .start();
    }

    @MainThread
    public void dispose() {
        handler.removeCallbacks(hideRunnable);
        cancelQueuedSteps();
        if (root != null && attached) {
            try {
                windowManager.removeView(root);
            } catch (IllegalArgumentException ignored) {
            }
        }
        attached = false;
        root = null;
        overlayCard = null;
        recycler = null;
    }

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
        SnapHelper snap = new LinearSnapHelper();
        snap.attachToRecyclerView(recycler);

        // Активность пользователя внутри оверлея (touch / drag / scroll / fling)
        // считается взаимодействием — auto-hide перезапускается. Иначе если
        // человек скроллит ленту выбирая режим, оверлей пропал бы через 3 секунды.
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
                return false; // не консьюмим — pill по-прежнему получает click
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {}

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });

        // Dismiss-by-tap внутри окна: тап в «глухие» области (padding карточки,
        // spacing между pills) закрывает оверлей. Тапы по pill потребляются ими
        // и сюда не доходят.
        root.setClickable(true);
        root.setOnClickListener(v -> hide());

        // Dismiss-by-tap снаружи окна: FLAG_WATCH_OUTSIDE_TOUCH вместе с
        // FLAG_NOT_TOUCH_MODAL даёт ровно один ACTION_OUTSIDE event при тапе
        // вне нашего окна. Сам тап при этом всё равно доходит до окна под
        // нами (карта / медиа / etc) — это и есть то, что мы хотим.
        root.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                hide();
                return true;
            }
            return false;
        });

        // Фиксируем ширину карточки на 80% экрана СРАЗУ (до первого layout pass) —
        // тогда RecyclerView внутри сразу получит правильный constraint и
        // центрирующий scroll будет считаться корректно.
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int maxWidthPx = (int) (dm.widthPixels * MAX_WIDTH_FRACTION);
        overlayCard.getLayoutParams().width = maxWidthPx;

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

    private void scrollToActive(int activeCode) {
        if (recycler == null) return;
        int idx = adapter.indexOf(activeCode);
        if (idx < 0) return;
        if (recycler.getChildCount() == 0
                || recycler.getWidth() == 0
                || recycler.getChildAt(0).getWidth() == 0) {
            recycler.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            if (recycler == null) return;
                            recycler.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            performScrollToCenter(idx);
                        }
                    });
        } else {
            recycler.post(() -> performScrollToCenter(idx));
        }
    }

    private void performScrollToCenter(int idx) {
        if (recycler == null) return;
        applyEdgePadding();
        RecyclerView.LayoutManager lm = recycler.getLayoutManager();
        if (lm == null) return;
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
        scroller.setTargetPosition(idx);
        lm.startSmoothScroll(scroller);
    }

    private void applyEdgePadding() {
        if (recycler == null || recycler.getChildCount() == 0) return;
        View firstChild = recycler.getChildAt(0);
        int pillWidth = firstChild.getWidth();
        if (pillWidth <= 0) return;
        int recyclerWidth = recycler.getWidth();
        if (recyclerWidth <= 0) return;
        int paddingHorizontal = (int) (context.getResources()
                .getDimension(R.dimen.overlay_padding_horizontal));
        int targetSideTotal = Math.max(paddingHorizontal,
                (recyclerWidth - pillWidth) / 2);
        if (recycler.getPaddingLeft() != targetSideTotal) {
            recycler.setPadding(
                    targetSideTotal,
                    recycler.getPaddingTop(),
                    targetSideTotal,
                    recycler.getPaddingBottom());
        }
    }

    private void scheduleAutoHide() {
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
