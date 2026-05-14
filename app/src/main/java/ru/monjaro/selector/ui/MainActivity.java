package ru.monjaro.selector.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Locale;

import ru.monjaro.selector.BuildConfig;
import ru.monjaro.selector.MonjaroSelectorApp;
import ru.monjaro.selector.R;
import ru.monjaro.selector.car.DriveModeCatalog;
import ru.monjaro.selector.car.DriveModeRepository;
import ru.monjaro.selector.databinding.ActivityMainBinding;
import ru.monjaro.selector.knob.KnobIntents;
import ru.monjaro.selector.service.DriveModeOverlayService;
import ru.monjaro.selector.settings.DriveModeSettings;
import ru.monjaro.selector.settings.ModeOrderEntry;
import ru.monjaro.selector.ui.modes.ModeDragCallback;
import ru.monjaro.selector.ui.modes.ModeListAdapter;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ModeListAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private DriveModeSettings settings;
    private boolean permissionDialogShown;

    private final DriveModeRepository.SupportedModesListener supportedListener =
            this::onSupportedModesChanged;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        settings = MonjaroSelectorApp.get(this).getSettings();
        setupList();
        setupControls();
        setupDurationSliders();
    }

    private void setupDurationSliders() {
        bindDurationSlider(
                binding.sliderPreview,
                binding.durationPreviewValue,
                settings.getAutoHidePreviewMs(),
                settings::setAutoHidePreviewMs);
        bindDurationSlider(
                binding.sliderSwitch,
                binding.durationSwitchValue,
                settings.getAutoHideSwitchMs(),
                settings::setAutoHideSwitchMs);
    }

    private void bindDurationSlider(@NonNull Slider slider,
                                    @NonNull TextView valueView,
                                    int initialMs,
                                    @NonNull java.util.function.IntConsumer onChanged) {
        slider.setValue(initialMs);
        valueView.setText(formatSeconds(initialMs));
        slider.addOnChangeListener((sl, value, fromUser) -> {
            valueView.setText(formatSeconds((int) value));
            if (fromUser) onChanged.accept((int) value);
        });
    }

    private String formatSeconds(int ms) {
        return String.format(Locale.getDefault(),
                getString(R.string.duration_value_seconds), ms / 1000f);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        DriveModeRepository.get().addSupportedModesListener(supportedListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        DriveModeRepository.get().removeSupportedModesListener(supportedListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadFromRepository();
        // Start the service if the permission is already granted (e.g. the
        // user just came back from system Settings after granting it).
        // Otherwise show the instruction dialog — but only once per session.
        if (hasOverlayPermission()) {
            MonjaroSelectorApp.get(this).startServiceIfPermitted();
        } else if (!permissionDialogShown) {
            showOverlayPermissionDialog();
        }
    }

    private void setupList() {
        adapter = new ModeListAdapter(new ModeListAdapter.Callback() {
            @Override
            public void onEnabledChanged(int code, boolean enabled) {
                settings.setEnabled(code, enabled);
            }

            @Override
            public void onOrderChanged(List<ModeOrderEntry> newOrder) {
                settings.saveOrder(newOrder);
            }

            @Override
            public void onStartDrag(RecyclerView.ViewHolder vh) {
                itemTouchHelper.startDrag(vh);
            }
        });
        binding.recyclerModes.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerModes.setAdapter(adapter);
        itemTouchHelper = new ItemTouchHelper(new ModeDragCallback(adapter));
        itemTouchHelper.attachToRecyclerView(binding.recyclerModes);
    }

    private void setupControls() {
        binding.btnShowIntegration.setOnClickListener(v -> showIntegrationDialog());

        binding.btnPreview.setOnClickListener(v -> {
            if (!hasOverlayPermission()) {
                showOverlayPermissionDialog();
                return;
            }
            Intent svc = new Intent(this, DriveModeOverlayService.class);
            svc.setAction(DriveModeOverlayService.ACTION_SHOW_PREVIEW);
            ContextCompat.startForegroundService(this, svc);
        });

        binding.btnResetOrder.setOnClickListener(v -> {
            settings.resetOrder();
            reloadFromRepository();
            Snackbar.make(binding.getRoot(), R.string.order_reset_toast, Snackbar.LENGTH_SHORT).show();
        });
    }

    private void reloadFromRepository() {
        int[] supported = DriveModeRepository.get().getSupportedModes();
        if (supported == null || supported.length == 0) {
            supported = DriveModeCatalog.defaultOrder();
        }
        List<ModeOrderEntry> merged = settings.mergeWithSupported(supported);
        adapter.submit(merged);
    }

    private void onSupportedModesChanged(@NonNull int[] supportedModes) {
        if (adapter == null) return;
        List<ModeOrderEntry> merged = settings.mergeWithSupported(supportedModes);
        adapter.submit(merged);
    }

    private void showIntegrationDialog() {
        LayoutInflater inf = LayoutInflater.from(this);
        View root = inf.inflate(R.layout.dialog_mconfig_integration, null);
        LinearLayout container = root.findViewById(R.id.dialog_container);

        addParagraph(container, getString(R.string.mconfig_intro));

        addSectionHeader(container, R.string.mconfig_section_switch);
        addCopyableRow(container, R.string.mconfig_label_prev, KnobIntents.ACTION_PREV_1);
        addCopyableRow(container, R.string.mconfig_label_next, KnobIntents.ACTION_NEXT_1);

        addSectionHeader(container, R.string.mconfig_section_series);
        addCopyableRow(container, R.string.mconfig_label_prev_2, KnobIntents.ACTION_PREV_2);
        addCopyableRow(container, R.string.mconfig_label_prev_3, KnobIntents.ACTION_PREV_3);
        addCopyableRow(container, R.string.mconfig_label_next_2, KnobIntents.ACTION_NEXT_2);
        addCopyableRow(container, R.string.mconfig_label_next_3, KnobIntents.ACTION_NEXT_3);

        addSectionHeader(container, R.string.mconfig_section_show);
        addCopyableRow(container, R.string.mconfig_label_show_overlay, KnobIntents.ACTION_SHOW_OVERLAY);

        addSectionHeader(container, R.string.mconfig_section_package);
        addCopyableRow(container, R.string.mconfig_label_package, getPackageName());

        addFooter(container, getString(R.string.mconfig_footer));

        new AlertDialog.Builder(this)
                .setTitle(R.string.mconfig_integration_dialog_title)
                .setView(root)
                .setPositiveButton(R.string.mconfig_integration_dialog_close, null)
                .show();
    }

    private void addSectionHeader(LinearLayout container, int textRes) {
        TextView tv = new TextView(this);
        tv.setText(textRes);
        tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        float d = getResources().getDisplayMetrics().density;
        tv.setPadding((int) (12 * d), (int) (16 * d), (int) (12 * d), (int) (4 * d));
        container.addView(tv);
    }

    private void addCopyableRow(LinearLayout container, int labelRes, String value) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_copyable, container, false);
        ((TextView) row.findViewById(R.id.copyable_label)).setText(labelRes);
        ((TextView) row.findViewById(R.id.copyable_value)).setText(value);
        row.setOnClickListener(v -> copyToClipboard(getString(labelRes), value));
        container.addView(row);
    }

    private void addParagraph(LinearLayout container, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        float d = getResources().getDisplayMetrics().density;
        tv.setPadding((int) (12 * d), (int) (4 * d), (int) (12 * d), (int) (8 * d));
        container.addView(tv);
    }

    private void addFooter(LinearLayout container, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        tv.setAlpha(0.75f);
        float d = getResources().getDisplayMetrics().density;
        tv.setPadding((int) (12 * d), (int) (16 * d), (int) (12 * d), (int) (4 * d));
        container.addView(tv);
    }

    private void showAboutDialog() {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_about, null);
        ((TextView) root.findViewById(R.id.about_app_name)).setText(R.string.app_name);
        ((TextView) root.findViewById(R.id.about_version))
                .setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));
        TextView email = root.findViewById(R.id.about_email);
        email.setOnClickListener(v -> {
            Intent mailto = new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("mailto:" + email.getText().toString()));
            try {
                startActivity(mailto);
            } catch (Throwable ignored) {
                copyToClipboard(getString(R.string.about_email), email.getText().toString());
            }
        });
        new AlertDialog.Builder(this)
                .setView(root)
                .setPositiveButton(R.string.about_close, null)
                .show();
    }

    private void copyToClipboard(@NonNull String label, @NonNull String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText(label, value));
        // On Android 13+ the system shows its own "Copied to clipboard" chip,
        // so we only emit a Toast on older versions.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.copied_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);
    }

    private void showOverlayPermissionDialog() {
        permissionDialogShown = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_overlay_title)
                .setMessage(R.string.permission_overlay_message)
                .setPositiveButton(R.string.permission_overlay_grant, (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.permission_overlay_cancel, null)
                .show();
    }
}
