package com.book.mask.ui;

import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.floating.FloatService;
import com.book.mask.network.AppConfigManager;
import com.book.mask.reminder.ProviderResult;
import com.book.mask.reminder.ReminderTextRepository;
import com.book.mask.reminder.config.ProviderPreset;
import com.book.mask.reminder.config.ProviderPresetCatalog;
import com.book.mask.reminder.config.ProviderSecretStore;
import com.book.mask.reminder.config.ReminderProviderConfig;
import com.book.mask.reminder.config.ReminderProviderConfigStore;
import com.book.mask.reminder.config.ReminderProviderConfigValidator;
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
    private TextView defaultDescription;
    private View detailsContainer;
    private View progressIndicator;
    private MaterialButton deleteButton;
    private MaterialButton testButton;
    private MaterialButton saveButton;
    private TextInputLayout providerNameLayout;
    private TextInputLayout endpointLayout;
    private TextInputLayout modelLayout;
    private TextInputLayout apiKeyLayout;
    private TextInputEditText providerNameInput;
    private TextInputEditText endpointInput;
    private TextInputEditText apiKeyInput;
    private ChipGroup presetGroup;
    private ChipGroup modelGroup;
    private View modelLabel;
    private TextInputEditText modelInput;
    private TextView testStatus;

    private String editingProfileId;
    private boolean apiKeyMasked;
    private String selectedPresetId = ReminderProviderConfig.OFFICIAL_PROFILE_ID;
    // Owner key of the models currently shown (preset id, or a custom provider's
    // profileId); used to add/remove that owner's saved custom models.
    private String currentModelOwnerKey;
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
        refreshDefaultModelDescription();
        showActiveProfile();
        return rootView;
    }

    private void bindViews() {
        defaultDescription = rootView.findViewById(R.id.tv_provider_default_description);
        detailsContainer = rootView.findViewById(R.id.container_provider_details);
        progressIndicator = rootView.findViewById(R.id.progress_provider_test);
        deleteButton = rootView.findViewById(R.id.btn_delete_provider);
        testButton = rootView.findViewById(R.id.btn_test_provider);
        saveButton = rootView.findViewById(R.id.btn_save_provider);
        providerNameLayout = rootView.findViewById(R.id.layout_provider_name);
        endpointLayout = rootView.findViewById(R.id.layout_provider_endpoint);
        modelLayout = rootView.findViewById(R.id.layout_provider_model);
        apiKeyLayout = rootView.findViewById(R.id.layout_provider_api_key);
        providerNameInput = rootView.findViewById(R.id.input_provider_name);
        endpointInput = rootView.findViewById(R.id.input_provider_endpoint);
        apiKeyInput = rootView.findViewById(R.id.input_provider_api_key);
        presetGroup = rootView.findViewById(R.id.group_provider_presets);
        modelGroup = rootView.findViewById(R.id.group_provider_models);
        modelLabel = rootView.findViewById(R.id.tv_provider_model_label);
        modelInput = rootView.findViewById(R.id.input_provider_model);
        testStatus = rootView.findViewById(R.id.tv_provider_test_status);
        rootView.setFocusableInTouchMode(true);

        providerNameInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        endpointInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        modelInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        disableAutofill(providerNameInput);
        disableAutofill(endpointInput);
        disableAutofill(modelInput);
        disableAutofill(apiKeyInput);
        apiKeyInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && apiKeyMasked) {
                apiKeyInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
                apiKeyMasked = false;
            }
        });
    }

    private void disableAutofill(View input) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
    }

    private void refreshDefaultModelDescription() {
        updateDefaultModelDescription();
        View currentView = rootView;
        new Thread(() -> {
            AppConfigManager.refreshConfig();
            currentView.post(() -> {
                if (rootView == currentView) {
                    updateDefaultModelDescription();
                }
            });
        }).start();
    }

    private void updateDefaultModelDescription() {
        defaultDescription.setText(getString(
                R.string.provider_default_description,
                AppConfigManager.getServerModel()));
    }

    private void bindActions() {
        rootView.findViewById(R.id.btn_provider_settings_back)
                .setOnClickListener(v -> getParentFragmentManager().popBackStack());

        deleteButton.setOnClickListener(v -> confirmDeleteSelectedProfile());
        testButton.setOnClickListener(v -> testCustomProvider());
        saveButton.setOnClickListener(v -> saveCustomProvider());
    }

    private void buildPresetChips() {
        presetGroup.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(presetGroup.getContext());
        addPresetChip(inflater, ReminderProviderConfig.OFFICIAL_PROFILE_ID,
                getString(R.string.provider_official_name));
        for (ProviderPreset preset : ProviderPresetCatalog.getAll()) {
            addPresetChip(inflater, preset.getId(), getString(preset.getNameResId()));
        }
        for (ReminderProviderConfig profile : configStore.getProfiles()) {
            if (ProviderPresetCatalog.CUSTOM_PRESET_ID.equals(profile.getPresetId())
                    && !ProviderPresetCatalog.CUSTOM_PRESET_ID.equals(profile.getProfileId())) {
                addPresetChip(inflater, profile.getProfileId(), profile.getProviderName());
            }
        }
        addPresetChip(inflater, ProviderPresetCatalog.CUSTOM_PRESET_ID,
                getString(R.string.provider_preset_custom));
    }

    private void addPresetChip(LayoutInflater inflater, String presetId, String label) {
        Chip chip = (Chip) inflater.inflate(
                R.layout.item_provider_preset_chip, presetGroup, false);
        chip.setText(label);
        chip.setTag(presetId);
        chip.setOnClickListener(v -> {
            String clickedPresetId = (String) v.getTag();
            selectPreset(clickedPresetId);
            v.post(() -> checkPresetChip(clickedPresetId));
        });
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
        buildPresetChips();
        ReminderProviderConfig active = configStore.getActiveConfig();
        String chipTag;
        if (active.isOfficial()) {
            selectedPresetId = ReminderProviderConfig.OFFICIAL_PROFILE_ID;
            chipTag = ReminderProviderConfig.OFFICIAL_PROFILE_ID;
        } else if (ProviderPresetCatalog.CUSTOM_PRESET_ID.equals(active.getPresetId())) {
            selectedPresetId = ProviderPresetCatalog.CUSTOM_PRESET_ID;
            chipTag = active.getProfileId();
        } else {
            selectedPresetId = active.getPresetId();
            chipTag = active.getPresetId();
        }
        checkPresetChip(chipTag);
        collapseDetails();
    }

    // Keep the form hidden until the user taps a preset chip; only highlight
    // which provider is currently active.
    private void collapseDetails() {
        editingProfileId = null;
        showDetails(false);
        deleteButton.setVisibility(View.GONE);
        clearTestStatus();
    }

    private void selectPreset(String id) {
        if (ReminderProviderConfig.OFFICIAL_PROFILE_ID.equals(id)) {
            selectedPresetId = ReminderProviderConfig.OFFICIAL_PROFILE_ID;
            selectOfficial();
            return;
        }
        if (ProviderPresetCatalog.CUSTOM_PRESET_ID.equals(id)) {
            // The "添加" chip always starts a brand-new custom provider. Real saved
            // custom providers carry a unique profileId (UUID) as their chip tag, so
            // we must not look one up here — otherwise stale data stored under the
            // legacy profileId "custom" would hijack this into edit-mode and every
            // save would overwrite that one hidden profile instead of adding a chip.
            selectedPresetId = ProviderPresetCatalog.CUSTOM_PRESET_ID;
            loadPresetDefaults(ProviderPresetCatalog.CUSTOM_PRESET_ID);
            return;
        }
        ReminderProviderConfig saved = configStore.getProfile(id);
        if (saved != null) {
            // A saved profile carries its own presetId ("custom" for user-added
            // providers, the catalog id otherwise); the tapped chip tag is its profileId.
            selectedPresetId = saved.getPresetId();
            loadSavedConfig(saved);
            return;
        }
        selectedPresetId = id;
        loadPresetDefaults(id);
    }

    private void selectOfficial() {
        editingProfileId = null;
        showDetails(false);
        deleteButton.setVisibility(View.GONE);
        clearTestStatus();
        activateOfficialIfNeeded();
    }

    private void loadSavedConfig(ReminderProviderConfig profile) {
        editingProfileId = profile.getProfileId();
        providerNameInput.setText(profile.getProviderName());
        endpointInput.setText(profile.getEndpointUrl());
        updateModelOptions(
                ProviderPresetCatalog.getById(profile.getPresetId()),
                modelOwnerKey(profile.getPresetId(), profile.getProfileId()),
                profile.getModel());
        apiKeyMasked = false;
        try {
            String apiKey = secretStore.getApiKey(profile.getProfileId());
            apiKeyMasked = apiKey != null && !apiKey.isEmpty();
            apiKeyInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
            apiKeyInput.setText(apiKeyMasked ? apiKey : "");
        } catch (GeneralSecurityException e) {
            apiKeyInput.setText("");
        }
        apiKeyLayout.setHint(apiKeyMasked
                ? R.string.provider_api_key_saved
                : R.string.provider_api_key);
        providerNameLayout.setVisibility(isCustomPreset() ? View.VISIBLE : View.GONE);
        showDetails(true);
        // Only user-added custom providers can be removed; preset providers can't.
        deleteButton.setVisibility(isCustomPreset() ? View.VISIBLE : View.GONE);
        clearTestStatus();
        // Selecting a fully configured provider is enough to switch to it.
        // Without a stored key we can't use it yet, so keep the form open for
        // the user to enter one and activate through "test & enable" instead.
        if (apiKeyMasked) {
            activateProfileIfNeeded(profile.getProfileId());
        }
    }

    private void loadPresetDefaults(String presetId) {
        editingProfileId = null;
        ProviderPreset preset = ProviderPresetCatalog.getById(presetId);
        boolean customPreset = isCustomPreset();
        providerNameLayout.setVisibility(customPreset ? View.VISIBLE : View.GONE);
        providerNameInput.setText("");
        if (preset == null) {
            endpointInput.setText("");
            updateModelOptions(null, modelOwnerKey(presetId, editingProfileId), "");
        } else {
            endpointInput.setText(preset.getEndpointUrl());
            updateModelOptions(preset, presetId, preset.getDefaultModel());
        }
        apiKeyMasked = false;
        apiKeyInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        apiKeyInput.setText("");
        apiKeyLayout.setHint(R.string.provider_api_key);
        showDetails(true);
        deleteButton.setVisibility(View.GONE);
        clearTestStatus();
    }

    private void activateOfficialIfNeeded() {
        if (configStore.getActiveConfig().isOfficial()) {
            return;
        }
        configStore.activateOfficial();
        applyConfigurationChange();
    }

    private void activateProfileIfNeeded(String profileId) {
        if (profileId.equals(configStore.getActiveConfig().getProfileId())) {
            return;
        }
        if (configStore.activate(profileId)) {
            applyConfigurationChange();
        }
    }

    // Every provider (preset or custom) shows its models as a chip row plus a
    // trailing "添加" chip. The chip list = the preset's catalog models (none for a
    // custom provider) merged with any model names the user has saved for this owner.
    // ownerKey namespaces the saved models: preset id for presets, profileId for
    // custom providers (each custom provider keeps its own model list); null when a
    // brand-new custom provider hasn't been saved yet.
    private void updateModelOptions(ProviderPreset preset, String ownerKey, String selectedModel) {
        currentModelOwnerKey = ownerKey;
        modelLabel.setVisibility(View.VISIBLE);
        modelGroup.setVisibility(View.VISIBLE);
        List<String> customModels = configStore.getCustomModels(ownerKey);
        List<String> models = new java.util.ArrayList<>();
        if (preset != null) {
            models.addAll(preset.getModels());
        }
        for (String custom : customModels) {
            if (!models.contains(custom)) {
                models.add(custom);
            }
        }
        buildModelChips(models, customModels, selectedModel);
    }

    private String modelOwnerKey(String presetId, String profileId) {
        return ProviderPresetCatalog.CUSTOM_PRESET_ID.equals(presetId) ? profileId : presetId;
    }

    private void buildModelChips(
            List<String> models, List<String> deletableModels, String selectedModel) {
        modelGroup.setOnCheckedStateChangeListener(null);
        modelGroup.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(modelGroup.getContext());
        // With no known models (e.g. a brand-new custom provider) fall back to the
        // "添加" free-text chip so the user can type the first model name.
        boolean customModel = models.isEmpty()
                || (selectedModel != null && !selectedModel.isEmpty()
                        && !models.contains(selectedModel));
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
            // User-added models (not catalog ones) can be long-pressed to delete.
            if (deletableModels.contains(model)) {
                chip.setOnLongClickListener(v -> {
                    confirmDeleteCustomModel(model);
                    return true;
                });
            }
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

    private void confirmDeleteCustomModel(String model) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.model_delete_title)
                .setMessage(getString(R.string.model_delete_message, model))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteCustomModel(model))
                .show();
    }

    private void deleteCustomModel(String model) {
        String ownerKey = currentModelOwnerKey;
        if (ownerKey == null || !configStore.removeCustomModel(ownerKey, model)) {
            return;
        }
        // Keep the current pick unless it was the deleted model, then reset selection.
        String selectedBefore = resolveModel();
        String newSelected = model.equals(selectedBefore) ? "" : selectedBefore;
        updateModelOptions(
                ProviderPresetCatalog.getById(selectedPresetId), ownerKey, newSelected);
        UiFeedback.show(rootView, getString(R.string.model_deleted));
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

    private void showCheckAfterText(Chip chip) {
        chip.setCheckedIconVisible(false);
    }

    private boolean isCustomPreset() {
        return ProviderPresetCatalog.CUSTOM_PRESET_ID.equals(selectedPresetId);
    }

    private String resolveModel() {
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

    private void saveCustomProvider() {
        ProviderDraft providerDraft = buildProviderDraft();
        if (providerDraft == null) {
            return;
        }
        clearFocusAndHideKeyboard();
        persistCustomProvider(providerDraft.config, providerDraft.apiKey);
    }

    private void clearFocusAndHideKeyboard() {
        rootView.requestFocus();
        InputMethodManager inputMethodManager = requireContext()
                .getSystemService(InputMethodManager.class);
        inputMethodManager.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
    }

    private void testCustomProvider() {
        ProviderDraft providerDraft = buildProviderDraft();
        if (providerDraft == null) {
            return;
        }

        int generation = ++requestGeneration;
        setBusy(true);
        showTestStatus(getString(R.string.provider_testing), false);
        repository.testCustomProvider(providerDraft.config, providerDraft.apiKey, new ReminderTextRepository.Callback() {
            @Override
            public void onSuccess(String text) {
                if (!canHandleResult(generation)) {
                    return;
                }
                setBusy(false);
                showTestStatus(getString(R.string.provider_test_success, text), true);
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

    private ProviderDraft buildProviderDraft() {
        boolean custom = isCustomPreset();
        // Custom providers each get a stable unique profileId (new one when adding,
        // the edited one when modifying); preset providers keep profileId == presetId.
        String profileId = custom
                ? (editingProfileId != null ? editingProfileId : configStore.newProfileId())
                : selectedPresetId;
        String providerName = resolveProviderName();
        String endpoint = textOf(endpointInput);
        String model = resolveModel();
        String apiKey;
        try {
            apiKey = resolveApiKey(profileId, endpoint);
        } catch (GeneralSecurityException e) {
            UiFeedback.showError(rootView, getString(R.string.provider_key_read_failed));
            return null;
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
                existing == null ? 0 : existing.getLastVerifiedAt());
        ReminderProviderConfigValidator.Error validationError =
                ReminderProviderConfigValidator.validate(draft, apiKey);
        if (validationError != null) {
            showValidationError(validationError);
            return null;
        }
        return new ProviderDraft(draft, apiKey);
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
                            draft.getLastVerifiedAt()));
            rememberCustomModel(saved);
            applyConfigurationChange();
            buildPresetChips();
            selectPreset(saved.getProfileId());
            checkPresetChip(saved.getProfileId());
            UiFeedback.show(rootView, getString(R.string.custom_provider_enabled));
        } catch (GeneralSecurityException e) {
            UiFeedback.showError(rootView, getString(R.string.provider_key_save_failed));
        }
    }

    // Remember a newly chosen model so it comes back as its own chip next time.
    // Custom providers keep a per-profile model list (keyed by profileId); preset
    // providers only remember models that aren't already in their catalog.
    private void rememberCustomModel(ReminderProviderConfig saved) {
        if (ProviderPresetCatalog.CUSTOM_PRESET_ID.equals(saved.getPresetId())) {
            configStore.addCustomModel(saved.getProfileId(), saved.getModel());
            return;
        }
        ProviderPreset preset = ProviderPresetCatalog.getById(saved.getPresetId());
        if (preset != null && !preset.getModels().contains(saved.getModel())) {
            configStore.addCustomModel(saved.getPresetId(), saved.getModel());
        }
    }

    private String resolveProviderName() {
        if (isCustomPreset()) {
            return textOf(providerNameInput);
        }
        ProviderPreset preset = ProviderPresetCatalog.getById(selectedPresetId);
        return preset == null ? "" : getString(preset.getNameResId());
    }

    private String resolveApiKey(String profileId, String endpoint)
            throws GeneralSecurityException {
        if (apiKeyMasked) {
            return secretStore.getApiKey(profileId);
        }
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
            case EMPTY_NAME:
            case NAME_TOO_LONG:
                UiFeedback.showInputError(providerNameLayout, providerNameInput, message);
                break;
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
        deleteButton.setEnabled(!busy);
        testButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        setChipsEnabled(presetGroup, !busy);
        setChipsEnabled(modelGroup, !busy);
        providerNameInput.setEnabled(!busy);
        endpointInput.setEnabled(!busy);
        modelInput.setEnabled(!busy);
        apiKeyInput.setEnabled(!busy);
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

    private static final class ProviderDraft {
        final ReminderProviderConfig config;
        final String apiKey;

        ProviderDraft(ReminderProviderConfig config, String apiKey) {
            this.config = config;
            this.apiKey = apiKey;
        }
    }

    @Override
    public void onDestroyView() {
        requestGeneration++;
        repository.cancelProviderTest();
        rootView = null;
        super.onDestroyView();
    }
}
