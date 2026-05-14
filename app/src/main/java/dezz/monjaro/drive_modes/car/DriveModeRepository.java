package dezz.monjaro.drive_modes.car;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.ecarx.xui.adaptapi.FunctionStatus;
import com.ecarx.xui.adaptapi.car.Car;
import com.ecarx.xui.adaptapi.car.ICar;
import com.ecarx.xui.adaptapi.car.base.ICarFunction;
import com.ecarx.xui.adaptapi.car.vehicle.IDriveMode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import dezz.monjaro.drive_modes.util.Logs;

/**
 * Single point of contact with the ECarX SDK for drive mode.
 *
 * All SDK calls happen on a dedicated "car-io" thread.
 * Listeners are notified on the UI thread.
 *
 * Echo protection: every setMode call records the expected value in
 * programmaticInFlight with a deadline; while the deadline has not expired,
 * a matching value coming back from ECU is classified as PROGRAMMATIC.
 */
public final class DriveModeRepository {

    private static final int FUNC = IDriveMode.DM_FUNC_DRIVE_MODE_SELECT;
    private static final long PROGRAMMATIC_TTL_MS = 700L;
    private static final long DEDUP_WINDOW_MS = 250L;
    private static final int MAX_WATCHER_RETRIES = 3;
    private static final long WATCHER_RETRY_BASE_MS = 2000L;

    private static volatile DriveModeRepository INSTANCE;

    private final HandlerThread ioThread;
    private final Handler ioHandler;
    private final Handler uiHandler;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SupportedModesListener> supportedListeners =
            new CopyOnWriteArrayList<>();

    private volatile ICar car;
    private volatile ICarFunction carFunction;
    private volatile int[] supportedModes = new int[0];
    private volatile int lastKnownMode = -1;
    private volatile long lastEventAt = 0L;
    private volatile boolean initialized;

    private final Map<Integer, Long> programmaticInFlight = new HashMap<>();
    private final Object inFlightLock = new Object();

    private ICarFunction.IFunctionValueWatcher watcher;
    private int watcherRetries = 0;

    public interface Listener {
        @MainThread
        void onModeChanged(int previousValue, int newValue, @NonNull DriveModeChangeOrigin origin);
    }

    public interface SupportedModesListener {
        @MainThread
        void onSupportedModesChanged(@NonNull int[] supportedModes);
    }

    private DriveModeRepository() {
        ioThread = new HandlerThread("car-io");
        ioThread.start();
        ioHandler = new Handler(ioThread.getLooper());
        uiHandler = new Handler(Looper.getMainLooper());
    }

    public static DriveModeRepository get() {
        DriveModeRepository local = INSTANCE;
        if (local == null) {
            synchronized (DriveModeRepository.class) {
                local = INSTANCE;
                if (local == null) {
                    INSTANCE = local = new DriveModeRepository();
                }
            }
        }
        return local;
    }

    @AnyThread
    public void init(@NonNull Context context) {
        Context appCtx = context.getApplicationContext();
        ioHandler.post(() -> initOnIo(appCtx));
    }

    @WorkerThread
    private void initOnIo(Context appCtx) {
        if (initialized) return;
        try {
            car = Car.create(appCtx);
            carFunction = car != null ? car.getICarFunction() : null;
            if (carFunction == null) {
                Logs.w("ICarFunction == null, retrying later");
                ioHandler.postDelayed(() -> initOnIo(appCtx), 1500);
                return;
            }
            loadSupported();
            try {
                int current = carFunction.getFunctionValue(FUNC);
                lastKnownMode = current;
                Logs.d("Initial mode: " + current);
            } catch (Throwable t) {
                Logs.w("Failed to read initial mode: " + t.getMessage());
            }
            watcher = new ICarFunction.IFunctionValueWatcher() {
                @Override
                public void onFunctionValueChanged(int functionId, int zone, int value) {
                    if (functionId != FUNC) return;
                    handleEvent(value);
                }

                @Override
                public void onCustomizeFunctionValueChanged(int functionId, int zone, float value) {}

                @Override
                public void onFunctionChanged(int functionId) {}

                @Override
                public void onSupportedFunctionStatusChanged(int functionId, int zone, FunctionStatus status) {}

                @Override
                public void onSupportedFunctionValueChanged(int functionId, int[] values) {
                    if (functionId == FUNC && values != null && values.length > 0) {
                        supportedModes = values.clone();
                        Logs.d("Supported modes updated, size=" + values.length);
                        notifySupportedChanged();
                    }
                }
            };
            registerWatcherWithRetry();
            initialized = true;
            Logs.d("DriveModeRepository init OK");
        } catch (Throwable t) {
            Logs.e("DriveModeRepository init failed", t);
            ioHandler.postDelayed(() -> initOnIo(appCtx), 3000);
        }
    }

    @WorkerThread
    private void registerWatcherWithRetry() {
        ICarFunction cf = carFunction;
        if (cf == null) return;
        try {
            cf.registerFunctionValueWatcher(FUNC, watcher);
            watcherRetries = 0;
            Logs.d("Watcher registered");
        } catch (Throwable t) {
            if (watcherRetries < MAX_WATCHER_RETRIES) {
                watcherRetries++;
                long delay = WATCHER_RETRY_BASE_MS * watcherRetries;
                Logs.w("registerFunctionValueWatcher failed: " + t.getMessage()
                        + ", retry #" + watcherRetries + " in " + delay + "ms");
                ioHandler.postDelayed(this::registerWatcherWithRetry, delay);
            } else {
                Logs.w("Watcher could not be registered after " + MAX_WATCHER_RETRIES + " attempts");
            }
        }
    }

    @WorkerThread
    private void loadSupported() {
        try {
            int[] s = carFunction.getSupportedFunctionValue(FUNC);
            if (s != null && s.length > 0) {
                supportedModes = s.clone();
                Logs.d("Supported modes: " + s.length);
                notifySupportedChanged();
                return;
            }
        } catch (Throwable t) {
            Logs.w("getSupportedFunctionValue failed: " + t.getMessage());
        }
        supportedModes = DriveModeCatalog.defaultOrder();
        Logs.d("Fallback to default set: " + supportedModes.length);
        notifySupportedChanged();
    }

    @WorkerThread
    private void handleEvent(int newValue) {
        long now = SystemClock.elapsedRealtime();
        int previous = lastKnownMode;
        lastKnownMode = newValue;

        if (previous == newValue && (now - lastEventAt) < DEDUP_WINDOW_MS) {
            return;
        }
        lastEventAt = now;

        DriveModeChangeOrigin origin = consumeProgrammatic(newValue, now)
                ? DriveModeChangeOrigin.PROGRAMMATIC
                : DriveModeChangeOrigin.EXTERNAL;

        Logs.d("Mode " + previous + " -> " + newValue + " (" + origin + ")");
        notifyListeners(previous, newValue, origin);
    }

    private boolean consumeProgrammatic(int value, long now) {
        synchronized (inFlightLock) {
            Iterator<Map.Entry<Integer, Long>> it = programmaticInFlight.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Long> e = it.next();
                if (e.getValue() < now) {
                    it.remove();
                }
            }
            Long deadline = programmaticInFlight.remove(value);
            return deadline != null && deadline >= now;
        }
    }

    private void notifyListeners(int previous, int newValue, DriveModeChangeOrigin origin) {
        for (Listener l : listeners) {
            uiHandler.post(() -> {
                try {
                    l.onModeChanged(previous, newValue, origin);
                } catch (Throwable t) {
                    Logs.w("Listener exception: " + t.getMessage(), t);
                }
            });
        }
    }

    private void notifySupportedChanged() {
        if (supportedListeners.isEmpty()) return;
        int[] snapshot = supportedModes.clone();
        for (SupportedModesListener l : supportedListeners) {
            uiHandler.post(() -> {
                try {
                    l.onSupportedModesChanged(snapshot);
                } catch (Throwable t) {
                    Logs.w("SupportedModesListener exception: " + t.getMessage(), t);
                }
            });
        }
    }

    @AnyThread
    public int getLastKnownMode() {
        return lastKnownMode;
    }

    /** Synchronously reads the current mode from the SDK. Do not call from the UI thread. */
    @WorkerThread
    public int readCurrentMode() {
        ICarFunction cf = carFunction;
        if (cf == null) return lastKnownMode;
        try {
            int v = cf.getFunctionValue(FUNC);
            lastKnownMode = v;
            return v;
        } catch (Throwable t) {
            return lastKnownMode;
        }
    }

    @AnyThread
    public void readCurrentModeAsync(@NonNull java.util.function.IntConsumer callback) {
        ioHandler.post(() -> {
            int v = readCurrentMode();
            uiHandler.post(() -> callback.accept(v));
        });
    }

    @NonNull
    public int[] getSupportedModes() {
        return supportedModes.clone();
    }

    public boolean isFunctionAvailable() {
        ICarFunction cf = carFunction;
        if (cf == null) return false;
        try {
            return cf.isFunctionSupported(FUNC) == FunctionStatus.active;
        } catch (Throwable t) {
            return false;
        }
    }

    @AnyThread
    public void setMode(int code, @NonNull DriveModeChangeOrigin origin) {
        ioHandler.post(() -> setModeOnIo(code, origin));
    }

    @WorkerThread
    private void setModeOnIo(int code, DriveModeChangeOrigin origin) {
        ICarFunction cf = carFunction;
        if (cf == null) {
            Logs.w("setMode: carFunction == null");
            return;
        }
        if (origin == DriveModeChangeOrigin.PROGRAMMATIC) {
            synchronized (inFlightLock) {
                programmaticInFlight.put(code, SystemClock.elapsedRealtime() + PROGRAMMATIC_TTL_MS);
            }
        }
        try {
            boolean ok = cf.setFunctionValue(FUNC, code);
            Logs.d("setFunctionValue(" + code + ") -> " + ok);
        } catch (Throwable t) {
            Logs.w("setFunctionValue failed: " + t.getMessage());
        }
    }

    @AnyThread
    public void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
    }

    @AnyThread
    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @AnyThread
    public void addSupportedModesListener(@NonNull SupportedModesListener listener) {
        if (supportedListeners.addIfAbsent(listener)) {
            int[] current = supportedModes.clone();
            if (current.length > 0) {
                uiHandler.post(() -> listener.onSupportedModesChanged(current));
            }
        }
    }

    @AnyThread
    public void removeSupportedModesListener(@NonNull SupportedModesListener listener) {
        supportedListeners.remove(listener);
    }
}
