package com.book.mask.ui;

import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.floating.FloatService;
import com.book.mask.network.reminder.ProviderPreset;
import com.book.mask.network.reminder.ProviderPresetCatalog;
import com.book.mask.network.reminder.ProviderResult;
import com.book.mask.network.reminder.ProviderSecretStore;
import com.book.mask.network.reminder.ReminderProviderConfig;
import com.book.mask.network.reminder.ReminderProviderConfigStore;
import com.book.mask.network.reminder.ReminderProviderConfigValidator;
import com.book.mask.network.reminder.ReminderTextRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.security.GeneralSecurityException;
import java.util.List;

public class ReminderProviderSettingsNav extends Fragment {
    private static final String CUSTOM_MODEL_TAG = "__custom_model__";

    private ReminderProviderConfigStore configStore;
    private ProviderSecretStore secretStore;
    private ReminderTextRepository repository;

    private View rootView;
    private View detailsContainer;
    private View progressIndicator;
    private MaterialButton activateButton;
    private MaterialButton deleteButton;
    private MaterialButton testButton;
    private MaterialButton saveButton;
    private TextInputLayout endpointLayout;
    private TextInputLayout modelLayout;
    private TextInputLayout apiKeyLayout;
    private TextInputEditText endpointInput;
    private TextInputEditText apiKeyInput;
    private ChipGroup presetGroup;
    private ChipGroup modelGroup;
    private View modelLabel;
    private TextInputEditText modelInput;
    private TextView testStatus;

    private String editingProfileId;
    private String selectedPresetId = ReminderProviderConfig.OFFICIAL_PROFILE_ID;
    private int requestGeneration;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_reminder_provider_settings, container, false);
        configStore = new ReminderProviderConfigStore();
        secretStore = new ProviderSecretStore(requireContext());
        repository = ReminderTextRepository.getInstance(requireContext());

        bindViews();
        bindActions();
        showActiveProfile();
        return rootView;
    }

    private void bindViews() {
        detailsContainer = rootView.findViewById(R.id.container_provider_details);
        progressIndicator = rootView.findViewById(R.id.progress_provider_test);
        activateButton = rootView.findViewById(R.id.btn_activate_provider);
        deleteButton = rootView.findViewById(R.id.btn_delete_provider);
        testButton = rootView.findViewById(R.id.btn_test_provider);
        saveButton = rootView.findViewById(R.id.btn_save_provider);
        endpointLayout = rootView.findViewById(R.id.layout_provider_endpoint);
        modelLayout = rootView.findViewById(R.id.layout_provider_model);
        apiKeyLayout = rootView.findViewById(R.id.layout_provider_api_key);
        endpointInput = rootView.findViewById(R.id.input_provider_endpoint);
        apiKeyInput = rootView.findViewById(R.id.input_provider_api_key);
        presetGroup = rootView.findViewById(R.id.group_provider_presets);
        modelGroup = rootView.findViewById(R.id.group_provider_models);
        modelLabel = rootView.findViewById(R.id.tv_provider_model_label);
        modelInput = rootView.findViewById(R.id.input_provider_model);
        testStatus = rootView.findViewById(R.id.tv_provider_test_status);

        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            apiKeyInput.setImportantForAutofill(
                    View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
    }

    private void bindActions() {
        rootView.findViewById(R.id.btn_provider_settings_back)
                .setOnClickListener(v -> getParentFragmentManager().popBackStack());

        buildPresetChips();

        activateButton.setOnClickListener(v -> activateSelectedProfile());
        deleteButton.setOnClickListener(v -> confirmDeleteSelectedProfile());
        testButton.setOnClickListener(v -> testCustomProvider(false));
        saveButton.setOnClickListener(v -> testCustomProvider(true));
    }

    private void buildPresetChips() {
        presetGroup.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(presetGroup.getContext());
        addPresetChip(inflater, ReminderProviderConfig.OFFICIAL_PROFILE_ID,
                getString(R.string.provider_official_name));
        for (ProviderPreset preset : ProviderPresetCatalog.getAll()) {
            addPresetChip(inflater, preset.getId(), getString(preset.getNameResId()));
        }
        addPresetChip(inflater, ProviderPresetCatalog.CUSTOM_PRESET_ID,
                getString(R.string.provider_preset_custom));
    }

    private void addPresetChip(LayoutInflater inflater, String presetId, String label) {
        Chip chip = (Chip) inflater.inflate(
                R.layout.item_provider_preset_chip, presetGroup, false);
        chip.setText(label);
        chip.setTag(presetId);
        chip.setOnClickListener(v -> selectPreset((String) v.getTag()));
        showCheckAfterText(chip);
        presetGroup.addView(chip);
    }

    private void checkPresetChip(String presetId) {
        for (int i = 0; i < presetGroup.getChildCount(); i++) {
            Chip chip = (Chip) presetGroup.getChildAt(i);
            chip.setChecked(presetId != null && presetId.equals(chip.getTag()));
        }
    }

    private void showDetails(boolean visible) {
        detailsContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showActiveProfile() {
        ReminderProviderConfig active = configStore.getActiveConfig();
        String presetId = active.isOfficial()
                ? ReminderProviderConfig.OFFICIAL_PROFILE_ID
                : active.getPresetId();
        selectedPresetId = presetId;
        checkPresetChip(presetId);
        collapseDetails();
    }

    // Keep the form hidden until the user taps a preset chip; only highlight
    // which provider is currently active.
    private void collapseDetails() {
        editingProfileId = null;
        showDetails(false);
        activateButton.setVisibility(View.GONE);
        deleteButton.setVisibility(View.GONE);
        clearTestStatus();
    }

    private void selectPreset(String presetId) {
        selectedPresetId = presetId;
        if (ReminderProviderConfig.OFFICIAL_PROFILE_ID.equals(presetId)) {
            selectOfficial();
            return;
        }
        ReminderProviderConfig saved = configStore.getProfile(presetId);
        if (saved != null) {
            loadSavedConfig(saved);
        } else {
            loadPresetDefaults(presetId);
        }
    }

    private void selectOfficial() {
        editingProfileId = null;
        showDetails(false);
        activateButton.setVisibility(
                configStore.getActiveConfig().isOfficial() ? View.GONE : View.VISIBLE);
        deleteButton.setVisibility(View.GONE);
        clearTestStatus();
    }

    private void loadSavedConfig(ReminderProviderConfig profile) {
        editingProfileId = profile.getProfileId();
        endpointInput.setText(profile.getEndpointUrl());
        updateModelOptions(
                ProviderPresetCatalog.getById(profile.getPresetId()), profile.getModel());
        apiKeyInput.setText("");
        apiKeyLayout.setHint(secretStore.hasApiKey(profile.getProfileId())
                ? R.string.provider_api_key_saved
                : R.string.provider_api_key);
        showDetails(true);
        boolean active = profile.getProfileId().equals(
                configStore.getActiveConfig().getProfileId());
        activateButton.setVisibility(active ? View.GONE : View.VISIBLE);
        deleteButton.setVisibility(View.VISIBLE);
        clearTestStatus();
    }

    private void loadPresetDefaults(String presetId) {
        editingProfileId = null;
        ProviderPreset preset = ProviderPresetCatalog.getById(presetId);
        if (preset == null) {
            endpointInput.setText("");
            updateModelOptions(null, "");
        } else {
            endpointInput.setText(preset.getEndpointUrl());
            updateModelOptions(preset, preset.getDefaultModel());
        }
        apiKeyInput.setText("");
        apiKeyLayout.setHint(R.string.provider_api_key);
        showDetails(true);
        activateButton.setVisibility(View.GONE);
        deleteButton.setVisibility(View.GONE);
        clearTestStatus();
    }

    private void updateModelOptions(ProviderPreset preset, String selectedModel) {
        if (preset == null) {
            modelLabel.setVisibility(View.GONE);
            modelGroup.setVisibility(View.GONE);
            modelGroup.setOnCheckedStateChangeListener(null);
            modelGroup.removeAllViews();
            modelLayout.setVisibility(View.VISIBLE);
            modelInput.setText(selectedModel == null ? "" : selectedModel);
        } else {
            modelLabel.setVisibility(View.VISIBLE);
            modelGroup.setVisibility(View.VISIBLE);
            buildModelChips(preset.getModels(), selectedModel);
        }
    }

    private void buildModelChips(List<String> models, String selectedModel) {
        modelGroup.setOnCheckedStateChangeListener(null);
        modelGroup.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(modelGroup.getContext());
        boolean customModel = selectedModel != null && !selectedModel.isEmpty()
                && !models.contains(selectedModel);
        String toCheck = selectedModel;
        if (!customModel && (toCheck == null || !models.contains(toCheck))) {
            toCheck = models.isEmpty() ? null : models.get(0);
        }
        for (String model : models) {
            Chip chip = (Chip) inflater.inflate(
                    R.layout.item_provider_preset_chip, modelGroup, false);
            chip.setText(model);
            chip.setTag(model);
            chip.setChecked(!customModel && model.equals(toCheck));
            showCheckAfterText(chip);
            modelGroup.addView(chip);
        }
        Chip customChip = (Chip) inflater.inflate(
                R.layout.item_provider_preset_chip, modelGroup, false);
        customChip.setText(getString(R.string.provider_preset_custom));
        customChip.setTag(CUSTOM_MODEL_TAG);
        customChip.setChecked(customModel);
        showCheckAfterText(customChip);
        modelGroup.addView(customChip);

        modelInput.setText(customModel ? selectedModel : "");
        modelGroup.setOnCheckedStateChangeListener(
                (group, checkedIds) -> updateCustomModelInput());
        updateCustomModelInput();
    }

    // Show the free-text model input only when the trailing "custom" model chip
    // is picked, so any preset provider can still target a hand-typed model.
    private void updateCustomModelInput() {
        boolean custom = isCustomModelSelected();
        modelLayout.setVisibility(custom ? View.VISIBLE : View.GONE);
        if (!custom) {
            modelLayout.setError(null);
        }
    }

    private boolean isCustomModelSelected() {
        for (int i = 0; i < modelGroup.getChildCount(); i++) {
            Chip chip = (Chip) modelGroup.getChildAt(i);
            if (chip.isChecked() && CUSTOM_MODEL_TAG.equals(chip.getTag())) {
                return true;
            }
        }
        return false;
    }

    // Move the selected marker to the right of the text: hide the default
    // leading checked icon and use the trailing close-icon slot as the tick.
    private void showCheckAfterText(Chip chip) {
        chip.setCheckedIconVisible(false);
        chip.setCloseIconResource(R.drawable.ic_chip_check);
        chip.setCloseIconTint(chip.getTextColors());
        chip.setCloseIconVisible(chip.isChecked());
        chip.setOnCheckedChangeListener((button, checked) -> chip.setCloseIconVisible(checked));
    }

    private boolean isCustomPreset() {
        return ProviderPresetCatalog.CUSTOM_PRESET_ID.equals(selectedPresetId);
    }

    private String resolveModel() {
        if (isCustomPreset()) {
            return textOf(modelInput);
        }
        for (int i = 0; i < modelGroup.getChildCount(); i++) {
            Chip chip = (Chip) modelGroup.getChildAt(i);
            if (chip.isChecked()) {
                return CUSTOM_MODEL_TAG.equals(chip.getTag())
                        ? textOf(modelInput)
                        : chip.getText().toString().trim();
            }
        }
        return "";
    }

    private void activateSelectedProfile() {
        boolean changed;
        if (ReminderProviderConfig.OFFICIAL_PROFILE_ID.equals(selectedPresetId)) {
            configStore.activateOfficial();
            changed = true;
        } else {
            changed = editingProfileId != null && configStore.activate(editingProfileId);
        }
        if (!changed) {
            return;
        }
        applyConfigurationChange();
        selectPreset(selectedPresetId);
        UiFeedback.show(rootView, getString(R.string.provider_activated));
    }

    private void confirmDeleteSelectedProfile() {
        if (editingProfileId == null) {
            return;
        }
        ReminderProviderConfig profile = configStore.getProfile(editingProfileId);
        if (profile == null) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.provider_delete_title)
                .setMessage(getString(R.string.provider_delete_message, profile.getProviderName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteProfile(profile))
                .show();
    }

    private void deleteProfile(ReminderProviderConfig profile) {
        repository.onConfigurationChanged();
        if (!configStore.delete(profile.getProfileId())) {
            return;
        }
        secretStore.deleteApiKey(profile.getProfileId());
        FloatService.notifyReminderProviderChanged();
        showActiveProfile();
        UiFeedback.show(rootView, getString(R.string.provider_deleted));
    }

    private void testCustomProvider(boolean activateAfterSuccess) {
        String profileId = selectedPresetId;
        String providerName = resolveProviderName();
        String endpoint = textOf(endpointInput);
        String model = resolveModel();
        String apiKey;
        try {
            apiKey = resolveApiKey(profileId, endpoint);
        } catch (GeneralSecurityException e) {
            UiFeedback.showError(rootView, getString(R.string.provider_key_read_failed));
            return;
        }

        ReminderProviderConfig existing = configStore.getProfile(profileId);
        ReminderProviderConfig draft = new ReminderProviderConfig(
                profileId,
                selectedPresetId,
                ReminderProviderConfig.ProviderType.OPENAI_CHAT,
                providerName,
                endpoint,
                model,
                existing == null ? 1 : existing.getConfigRevision(),
                0);
        ReminderProviderConfigValidator.Error validationError =
                ReminderProviderConfigValidator.validate(draft, apiKey);
        if (validationError != null) {
            showValidationError(validationError);
            return;
        }

        int generation = ++requestGeneration;
        setBusy(true);
        showTestStatus(getString(R.string.provider_testing), false);
        repository.testCustomProvider(draft, apiKey, new ReminderTextRepository.Callback() {
            @Override
            public void onSuccess(String text) {
                if (!canHandleResult(generation)) {
                    return;
                }
                setBusy(false);
                showTestStatus(getString(R.string.provider_test_success, text), true);
                if (activateAfterSuccess) {
                    persistCustomProvider(draft, apiKey);
                }
            }

            @Override
            public void onError(ProviderResult result) {
                if (!canHandleResult(generation)) {
                    return;
                }
                setBusy(false);
                String message = result.toUserMessage();
                showTestStatus(getString(R.string.provider_test_failed, message), false);
                UiFeedback.showError(rootView, message);
            }
        });
    }

    private void persistCustomProvider(ReminderProviderConfig draft, String apiKey) {
        try {
            secretStore.saveApiKey(draft.getProfileId(), apiKey.trim());
            ReminderProviderConfig saved = configStore.saveAndActivate(
                    new ReminderProviderConfig(
                            draft.getProfileId(),
                            draft.getPresetId(),
                            ReminderProviderConfig.ProviderType.OPENAI_CHAT,
                            draft.getProviderName(),
                            draft.getEndpointUrl(),
                            draft.getModel(),
                            draft.getConfigRevision(),
                            System.currentTimeMillis()));
            applyConfigurationChange();
            selectPreset(saved.getPresetId());
            UiFeedback.show(rootView, getString(R.string.custom_provider_enabled));
        } catch (GeneralSecurityException e) {
            UiFeedback.showError(rootView, getString(R.string.provider_key_save_failed));
        }
    }

    private String resolveProviderName() {
        ProviderPreset preset = ProviderPresetCatalog.getById(selectedPresetId);
        return preset != null
                ? getString(preset.getNameResId())
                : getString(R.string.provider_preset_custom);
    }

    private String resolveApiKey(String profileId, String endpoint)
            throws GeneralSecurityException {
        String enteredKey = textOf(apiKeyInput);
        if (!enteredKey.isEmpty()) {
            return enteredKey;
        }
        ReminderProviderConfig savedConfig = configStore.getProfile(profileId);
        return savedConfig != null && endpoint.equals(savedConfig.getEndpointUrl())
                ? secretStore.getApiKey(profileId)
                : null;
    }

    private void showValidationError(ReminderProviderConfigValidator.Error error) {
        String message = error.getMessage();
        switch (error) {
            case EMPTY_ENDPOINT:
            case ENDPOINT_TOO_LONG:
            case ENDPOINT_REQUIRES_HTTPS:
            case ENDPOINT_MISSING_HOST:
            case ENDPOINT_HAS_UNSUPPORTED_PARTS:
            case INVALID_ENDPOINT:
                UiFeedback.showInputError(endpointLayout, endpointInput, message);
                break;
            case EMPTY_MODEL:
            case MODEL_TOO_LONG:
                modelLayout.setError(message);
                modelInput.requestFocus();
                break;
            case EMPTY_API_KEY:
                UiFeedback.showInputError(apiKeyLayout, apiKeyInput, message);
                break;
            default:
                UiFeedback.showError(rootView, message);
                break;
        }
    }

    private void applyConfigurationChange() {
        repository.onConfigurationChanged();
        FloatService.notifyReminderProviderChanged();
    }

    private void setBusy(boolean busy) {
        progressIndicator.setVisibility(busy ? View.VISIBLE : View.GONE);
        activateButton.setEnabled(!busy);
        deleteButton.setEnabled(!busy);
        testButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        setChipsEnabled(presetGroup, !busy);
        setChipsEnabled(modelGroup, !busy);
        modelInput.setEnabled(!busy);
    }

    private void setChipsEnabled(ChipGroup group, boolean enabled) {
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setEnabled(enabled);
        }
    }

    private void showTestStatus(String message, boolean success) {
        testStatus.setText(message);
        testStatus.setTextColor(ContextCompat.getColor(
                requireContext(),
                success ? R.color.provider_status_success : R.color.provider_status_error));
        testStatus.setVisibility(View.VISIBLE);
    }

    private void clearTestStatus() {
        modelLayout.setError(null);
        testStatus.setText("");
        testStatus.setVisibility(View.GONE);
    }

    private boolean canHandleResult(int generation) {
        return generation == requestGeneration && isAdded() && rootView != null;
    }

    private static String textOf(TextView input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        requestGeneration++;
        repository.cancelProviderTest();
        rootView = null;
        super.onDestroyView();
    }
}
