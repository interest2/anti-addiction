package com.book.mask.ui;

import android.os.Bundle;
import android.os.Build;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.floating.FloatService;
import com.book.mask.network.reminder.ProviderResult;
import com.book.mask.network.reminder.ProviderSecretStore;
import com.book.mask.network.reminder.ReminderProviderConfig;
import com.book.mask.network.reminder.ReminderProviderConfigStore;
import com.book.mask.network.reminder.ReminderProviderConfigValidator;
import com.book.mask.network.reminder.ReminderTextRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.security.GeneralSecurityException;

public class ReminderProviderSettingsNav extends Fragment {
    private static final int[] REFRESH_INTERVAL_VALUES = {0, 15, 30, 60};
    private static final ProviderPreset[] PROVIDER_PRESETS = {
            new ProviderPreset(
                    R.string.provider_preset_openai,
                    "https://api.openai.com/v1/chat/completions",
                    "gpt-4.1-mini"),
            new ProviderPreset(
                    R.string.provider_preset_deepseek,
                    "https://api.deepseek.com/chat/completions",
                    "deepseek-v4-flash"),
            new ProviderPreset(
                    R.string.provider_preset_moonshot,
                    "https://api.moonshot.cn/v1/chat/completions",
                    "kimi-k2.6"),
            new ProviderPreset(
                    R.string.provider_preset_dashscope,
                    "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                    "qwen-plus"),
            new ProviderPreset(
                    R.string.provider_preset_zhipu,
                    "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                    "glm-4-flash"),
            new ProviderPreset(
                    R.string.provider_preset_mistral,
                    "https://api.mistral.ai/v1/chat/completions",
                    "mistral-small-latest")
    };

    private ReminderProviderConfigStore configStore;
    private ProviderSecretStore secretStore;
    private ReminderTextRepository repository;

    private View rootView;
    private View progressIndicator;
    private MaterialButton testButton;
    private MaterialButton saveButton;
    private TextInputLayout nameLayout;
    private TextInputLayout endpointLayout;
    private TextInputLayout modelLayout;
    private TextInputLayout apiKeyLayout;
    private TextInputEditText nameInput;
    private TextInputEditText endpointInput;
    private TextInputEditText modelInput;
    private TextInputEditText apiKeyInput;
    private MaterialAutoCompleteTextView presetInput;
    private MaterialAutoCompleteTextView refreshIntervalInput;
    private TextView activeStatus;
    private TextView testStatus;
    private int selectedRefreshInterval = ReminderProviderConfig.DEFAULT_REFRESH_INTERVAL_MINUTES;
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
        populateSavedConfiguration();
        return rootView;
    }

    private void bindViews() {
        progressIndicator = rootView.findViewById(R.id.progress_provider_test);
        testButton = rootView.findViewById(R.id.btn_test_provider);
        saveButton = rootView.findViewById(R.id.btn_save_provider);
        nameLayout = rootView.findViewById(R.id.layout_provider_name);
        endpointLayout = rootView.findViewById(R.id.layout_provider_endpoint);
        modelLayout = rootView.findViewById(R.id.layout_provider_model);
        apiKeyLayout = rootView.findViewById(R.id.layout_provider_api_key);
        nameInput = rootView.findViewById(R.id.input_provider_name);
        endpointInput = rootView.findViewById(R.id.input_provider_endpoint);
        modelInput = rootView.findViewById(R.id.input_provider_model);
        apiKeyInput = rootView.findViewById(R.id.input_provider_api_key);
        presetInput = rootView.findViewById(R.id.input_provider_preset);
        refreshIntervalInput = rootView.findViewById(R.id.input_provider_refresh_interval);
        activeStatus = rootView.findViewById(R.id.tv_provider_active_status);
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

        String[] presetNames = new String[PROVIDER_PRESETS.length];
        for (int i = 0; i < PROVIDER_PRESETS.length; i++) {
            presetNames[i] = getString(PROVIDER_PRESETS[i].nameResId);
        }
        presetInput.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                presetNames));
        presetInput.setOnItemClickListener((parent, view, position, id) ->
                applyPreset(PROVIDER_PRESETS[position]));

        String[] intervalLabels = getResources().getStringArray(R.array.provider_refresh_intervals);
        refreshIntervalInput.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                intervalLabels));
        refreshIntervalInput.setOnItemClickListener((parent, view, position, id) ->
                selectedRefreshInterval = REFRESH_INTERVAL_VALUES[position]);

        testButton.setOnClickListener(v -> testCustomProvider(false));
        saveButton.setOnClickListener(v -> testCustomProvider(true));
    }

    private void populateSavedConfiguration() {
        ReminderProviderConfig customConfig = configStore.getCustomConfig();
        nameInput.setText(customConfig.getProviderName());
        endpointInput.setText(customConfig.getEndpointUrl());
        modelInput.setText(customConfig.getModel());
        selectedRefreshInterval = customConfig.getRefreshIntervalMinutes();
        int refreshIntervalPosition = intervalPosition(selectedRefreshInterval);
        selectedRefreshInterval = REFRESH_INTERVAL_VALUES[refreshIntervalPosition];
        refreshIntervalInput.setText(
                getResources().getStringArray(R.array.provider_refresh_intervals)[
                        refreshIntervalPosition],
                false);

        if (secretStore.hasApiKey()) {
            apiKeyLayout.setHint(R.string.provider_api_key_saved);
        }

        updateActiveStatus();
    }

    private void testCustomProvider(boolean activateAfterSuccess) {
        String providerName = textOf(nameInput);
        String endpoint = textOf(endpointInput);
        String model = textOf(modelInput);
        String apiKey;
        try {
            apiKey = resolveApiKey(endpoint);
        } catch (GeneralSecurityException e) {
            UiFeedback.showError(rootView, getString(R.string.provider_key_read_failed));
            return;
        }

        ReminderProviderConfig draft = new ReminderProviderConfig(
                ReminderProviderConfig.CUSTOM_PROFILE_ID,
                ReminderProviderConfig.ProviderType.OPENAI_COMPATIBLE,
                providerName,
                endpoint,
                model,
                selectedRefreshInterval,
                configStore.getCustomConfig().getConfigRevision() + 1,
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
            secretStore.saveApiKey(apiKey.trim());
            configStore.saveAndActivateCustom(
                    draft.getProviderName(),
                    draft.getEndpointUrl(),
                    draft.getModel(),
                    draft.getRefreshIntervalMinutes(),
                    System.currentTimeMillis());
            apiKeyInput.setText("");
            apiKeyLayout.setHint(R.string.provider_api_key_saved);
            applyConfigurationChange();
            updateActiveStatus();
            UiFeedback.show(rootView, getString(R.string.custom_provider_enabled));
        } catch (GeneralSecurityException e) {
            UiFeedback.showError(rootView, getString(R.string.provider_key_save_failed));
        }
    }

    private String resolveApiKey(String endpoint) throws GeneralSecurityException {
        String enteredKey = textOf(apiKeyInput);
        if (!enteredKey.isEmpty()) {
            return enteredKey;
        }
        ReminderProviderConfig savedConfig = configStore.getCustomConfig();
        return endpoint.equals(savedConfig.getEndpointUrl()) ? secretStore.getApiKey() : null;
    }

    private void applyPreset(ProviderPreset preset) {
        nameInput.setText(preset.nameResId);
        endpointInput.setText(preset.endpoint);
        modelInput.setText(preset.model);
        clearTestStatus();
    }

    private void showValidationError(ReminderProviderConfigValidator.Error error) {
        String message = error.getMessage();
        switch (error) {
            case EMPTY_NAME:
            case NAME_TOO_LONG:
                UiFeedback.showInputError(nameLayout, nameInput, message);
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
                UiFeedback.showInputError(modelLayout, modelInput, message);
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

    private void updateActiveStatus() {
        ReminderProviderConfig active = configStore.getActiveConfig();
        activeStatus.setText(active.isOfficial()
                ? getString(R.string.provider_active_official)
                : getString(
                        R.string.provider_active_custom,
                        active.getProviderName(),
                        active.getModel()));
    }

    private void setBusy(boolean busy) {
        progressIndicator.setVisibility(busy ? View.VISIBLE : View.GONE);
        testButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        presetInput.setEnabled(!busy);
    }

    private void showTestStatus(String message, boolean success) {
        testStatus.setText(message);
        testStatus.setTextColor(ContextCompat.getColor(
                requireContext(),
                success ? R.color.provider_status_success : R.color.provider_status_error));
        testStatus.setVisibility(View.VISIBLE);
    }

    private void clearTestStatus() {
        testStatus.setText("");
        testStatus.setVisibility(View.GONE);
    }

    private boolean canHandleResult(int generation) {
        return generation == requestGeneration && isAdded() && rootView != null;
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static int intervalPosition(int value) {
        for (int i = 0; i < REFRESH_INTERVAL_VALUES.length; i++) {
            if (REFRESH_INTERVAL_VALUES[i] == value) {
                return i;
            }
        }
        return 0;
    }

    private static final class ProviderPreset {
        private final int nameResId;
        private final String endpoint;
        private final String model;

        private ProviderPreset(int nameResId, String endpoint, String model) {
            this.nameResId = nameResId;
            this.endpoint = endpoint;
            this.model = model;
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
