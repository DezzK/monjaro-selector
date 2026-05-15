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

package dezz.monjaro.drive_modes.ui;

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

import dezz.monjaro.drive_modes.BuildConfig;
import dezz.monjaro.drive_modes.MonjaroSelectorApp;
import dezz.monjaro.drive_modes.R;
import dezz.monjaro.drive_modes.car.DriveModeCatalog;
import dezz.monjaro.drive_modes.car.DriveModeDescriptor;
import dezz.monjaro.drive_modes.car.DriveModeRepository;
import dezz.monjaro.drive_modes.databinding.ActivityMainBinding;
import dezz.monjaro.drive_modes.knob.KnobIntents;
import dezz.monjaro.drive_modes.service.DriveModeOverlayService;
import dezz.monjaro.drive_modes.settings.DriveModeSettings;
import dezz.monjaro.drive_modes.settings.ModeOrderEntry;
import dezz.monjaro.drive_modes.ui.modes.ModeDragCallback;
import dezz.monjaro.drive_modes.ui.modes.ModeListAdapter;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_PERMISSION_DIALOG_SHOWN = "permission_dialog_shown";

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
        if (savedInstanceState != null) {
            permissionDialogShown = savedInstanceState.getBoolean(
                    STATE_PERMISSION_DIALOG_SHOWN, false);
        }
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

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_PERMISSION_DIALOG_SHOWN, permissionDialogShown);
    }

    @Override
    protected void onDestroy() {
        // Drop the binding so the inflated view hierarchy can be GC'd; AppCompat
        // doesn't do this for us.
        binding = null;
        super.onDestroy();
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
            public void onRemove(int code) {
                disableMode(code);
            }

            @Override
            public void onOrderChanged(List<Integer> newOrder) {
                applyEnabledOrder(newOrder);
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

        binding.btnProbe.setOnClickListener(v -> showProbeConfirm());

        binding.switchCarousel.setChecked(settings.isCarouselMode());
        binding.switchCarousel.setOnCheckedChangeListener(
                (btn, checked) -> settings.setCarouselMode(checked));
    }

    private void reloadFromRepository() {
        int[] supported = DriveModeRepository.get().getSupportedModes();
        if (supported == null || supported.length == 0) {
            supported = DriveModeCatalog.defaultOrder();
        }
        rebindLists(settings.mergeWithSupported(supported));
    }

    private void onSupportedModesChanged(@NonNull int[] supportedModes) {
        if (adapter == null) return;
        rebindLists(settings.mergeWithSupported(supportedModes));
    }

    /**
     * Splits the merged order into two visual sections: enabled (RecyclerView,
     * draggable) and disabled (chips, tap to add).
     */
    private void rebindLists(@NonNull List<ModeOrderEntry> merged) {
        List<Integer> enabled = new java.util.ArrayList<>();
        List<Integer> disabled = new java.util.ArrayList<>();
        for (ModeOrderEntry e : merged) {
            if (e.enabled) enabled.add(e.code);
            else disabled.add(e.code);
        }
        adapter.submit(enabled);
        binding.enabledEmptyHint.setVisibility(enabled.isEmpty() ? View.VISIBLE : View.GONE);
        rebuildAvailableChips(disabled);
    }

    private void rebuildAvailableChips(@NonNull List<Integer> available) {
        binding.chipsAvailable.removeAllViews();
        binding.availableEmptyHint.setVisibility(available.isEmpty() ? View.VISIBLE : View.GONE);
        for (int code : available) {
            DriveModeDescriptor desc = DriveModeCatalog.byCodeOrGeneric(code);
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
            chip.setText(getString(desc.labelRes));
            chip.setChipIconResource(desc.iconRes);
            chip.setChipIconTint(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, desc.accentRes)));
            chip.setChipIconVisible(true);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> enableMode(code));
            binding.chipsAvailable.addView(chip);
        }
    }

    /**
     * Move {@code code} from "available" to the END of "enabled" preserving
     * the order of every other entry.
     */
    private void enableMode(int code) {
        List<ModeOrderEntry> full = settings.getOrder();
        List<ModeOrderEntry> enabled = new java.util.ArrayList<>();
        List<ModeOrderEntry> disabled = new java.util.ArrayList<>();
        ModeOrderEntry target = null;
        for (ModeOrderEntry e : full) {
            if (e.code == code) {
                target = e;
            } else if (e.enabled) {
                enabled.add(e);
            } else {
                disabled.add(e);
            }
        }
        if (target == null) target = new ModeOrderEntry(code, true);
        target.enabled = true;
        enabled.add(target);
        List<ModeOrderEntry> result = new java.util.ArrayList<>(enabled);
        result.addAll(disabled);
        settings.saveOrder(result);
        reloadFromRepository();
    }

    /**
     * Move {@code code} from "enabled" to the START of "disabled" preserving
     * the order of every other entry. Done as one save so the JSON always
     * keeps the invariant: enabled first, disabled after.
     */
    private void disableMode(int code) {
        List<ModeOrderEntry> full = settings.getOrder();
        List<ModeOrderEntry> enabled = new java.util.ArrayList<>();
        List<ModeOrderEntry> disabled = new java.util.ArrayList<>();
        ModeOrderEntry target = null;
        for (ModeOrderEntry e : full) {
            if (e.code == code) {
                target = e;
            } else if (e.enabled) {
                enabled.add(e);
            } else {
                disabled.add(e);
            }
        }
        if (target != null) {
            target.enabled = false;
            disabled.add(0, target);
        }
        List<ModeOrderEntry> result = new java.util.ArrayList<>(enabled);
        result.addAll(disabled);
        settings.saveOrder(result);
        reloadFromRepository();
    }

    /**
     * Drag-and-drop committed: persist the new enabled order, keeping the
     * disabled tail intact.
     */
    private void applyEnabledOrder(@NonNull List<Integer> newEnabledOrder) {
        List<ModeOrderEntry> full = settings.getOrder();
        java.util.Map<Integer, ModeOrderEntry> byCode = new java.util.HashMap<>();
        for (ModeOrderEntry e : full) byCode.put(e.code, e);
        List<ModeOrderEntry> result = new java.util.ArrayList<>();
        for (int code : newEnabledOrder) {
            ModeOrderEntry e = byCode.remove(code);
            if (e != null) {
                e.enabled = true;
                result.add(e);
            }
        }
        // Append remaining (= disabled) entries preserving their relative order.
        for (ModeOrderEntry e : full) {
            if (byCode.containsKey(e.code)) {
                e.enabled = false;
                result.add(e);
                byCode.remove(e.code);
            }
        }
        settings.saveOrder(result);
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
        addCopyableRow(container, R.string.mconfig_label_show_overlay, KnobIntents.ACTION_SHOW);

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
        TextView telegram = root.findViewById(R.id.about_telegram);
        telegram.setOnClickListener(v -> {
            String text = telegram.getText().toString();
            String handle = (text.startsWith("@") && text.length() > 1)
                    ? text.substring(1)
                    : text;
            if (handle.isEmpty()) return;
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + handle));
            try {
                startActivity(open);
            } catch (Throwable ignored) {
                copyToClipboard(getString(R.string.about_telegram), text);
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

    // ------------------------------------------------------------------
    // Probe — auto-detect which modes the car supports.
    // ------------------------------------------------------------------

    private void showProbeConfirm() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.probe_confirm_title)
                .setMessage(R.string.probe_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.probe_confirm_run, (d, w) -> startProbe())
                .show();
    }

    private void startProbe() {
        java.util.List<dezz.monjaro.drive_modes.car.DriveModeDescriptor> all =
                DriveModeCatalog.all();
        int[] codes = new int[all.size()];
        for (int i = 0; i < codes.length; i++) codes[i] = all.get(i).code;

        TextView progressText = new TextView(this);
        progressText.setText(getString(R.string.probe_progress, 0, codes.length));
        progressText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        float d = getResources().getDisplayMetrics().density;
        progressText.setPadding((int) (24 * d), (int) (24 * d), (int) (24 * d), (int) (24 * d));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.probe_title)
                .setView(progressText)
                .setCancelable(false)
                .create();
        dialog.show();

        DriveModeRepository.get().probeSupportedModes(codes, 250,
                new DriveModeRepository.ProbeCallback() {
                    @Override
                    public void onProgress(int index, int total, int code) {
                        progressText.setText(getString(R.string.probe_progress, index, total));
                    }

                    @Override
                    public void onComplete(@NonNull int[] supported) {
                        if (dialog.isShowing()) dialog.dismiss();
                        Snackbar.make(binding.getRoot(),
                                        getString(R.string.probe_result,
                                                supported.length, codes.length),
                                        Snackbar.LENGTH_LONG)
                                .show();
                        // The repository already published supported, listener
                        // will refresh UI, but call directly to be safe.
                        if (supported.length > 0) onSupportedModesChanged(supported);
                    }

                    @Override
                    public void onFailed() {
                        if (dialog.isShowing()) dialog.dismiss();
                        Snackbar.make(binding.getRoot(),
                                        R.string.probe_failed,
                                        Snackbar.LENGTH_LONG)
                                .show();
                    }
                });
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
