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
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.security.GeneralSecurityException;

public class ReminderProviderSettingsNav extends Fragment {
    private static final int[] REFRESH_INTERVAL_VALUES = {0, 15, 30, 60};

    private ReminderProviderConfigStore configStore;
    private ProviderSecretStore secretStore;
    private ReminderTextRepository repository;

    private View rootView;
    private View customSettings;
    private View progressIndicator;
    private MaterialButtonToggleGroup providerToggle;
    private MaterialButton testButton;
    private MaterialButton saveButton;
    private TextInputLayout endpointLayout;
    private TextInputLayout modelLayout;
    private TextInputLayout apiKeyLayout;
    private TextInputEditText endpointInput;
    private TextInputEditText modelInput;
    private TextInputEditText apiKeyInput;
    private MaterialButtonToggleGroup authToggle;
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
        customSettings = rootView.findViewById(R.id.group_custom_provider_settings);
        progressIndicator = rootView.findViewById(R.id.progress_provider_test);
        providerToggle = rootView.findViewById(R.id.toggle_provider_type);
        testButton = rootView.findViewById(R.id.btn_test_provider);
        saveButton = rootView.findViewById(R.id.btn_save_provider);
        endpointLayout = rootView.findViewById(R.id.layout_provider_endpoint);
        modelLayout = rootView.findViewById(R.id.layout_provider_model);
        apiKeyLayout = rootView.findViewById(R.id.layout_provider_api_key);
        endpointInput = rootView.findViewById(R.id.input_provider_endpoint);
        modelInput = rootView.findViewById(R.id.input_provider_model);
        apiKeyInput = rootView.findViewById(R.id.input_provider_api_key);
        authToggle = rootView.findViewById(R.id.toggle_provider_auth);
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

        providerToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            boolean customSelected = checkedId == R.id.btn_provider_custom;
            customSettings.setVisibility(customSelected ? View.VISIBLE : View.GONE);
            testButton.setVisibility(customSelected ? View.VISIBLE : View.GONE);
            saveButton.setText(customSelected
                    ? R.string.test_and_enable_provider
                    : R.string.enable_official_provider);
            clearTestStatus();
        });

        authToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            boolean bearer = checkedId == R.id.btn_provider_auth_bearer;
            apiKeyLayout.setVisibility(bearer ? View.VISIBLE : View.GONE);
        });

        String[] intervalLabels = getResources().getStringArray(R.array.provider_refresh_intervals);
        refreshIntervalInput.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                intervalLabels));
        refreshIntervalInput.setOnItemClickListener((parent, view, position, id) ->
                selectedRefreshInterval = REFRESH_INTERVAL_VALUES[position]);

        testButton.setOnClickListener(v -> testCustomProvider(false));
        saveButton.setOnClickListener(v -> {
            if (providerToggle.getCheckedButtonId() == R.id.btn_provider_official) {
                activateOfficialProvider();
            } else {
                testCustomProvider(true);
            }
        });
    }

    private void populateSavedConfiguration() {
        ReminderProviderConfig customConfig = configStore.getCustomConfig();
        endpointInput.setText(customConfig.getEndpointUrl());
        modelInput.setText(customConfig.getModel());
        selectedRefreshInterval = customConfig.getRefreshIntervalMinutes();
        int refreshIntervalPosition = intervalPosition(selectedRefreshInterval);
        selectedRefreshInterval = REFRESH_INTERVAL_VALUES[refreshIntervalPosition];
        refreshIntervalInput.setText(
                getResources().getStringArray(R.array.provider_refresh_intervals)[
                        refreshIntervalPosition],
                false);

        authToggle.check(customConfig.getAuthType() == ReminderProviderConfig.AuthType.NONE
                ? R.id.btn_provider_auth_none
                : R.id.btn_provider_auth_bearer);
        if (secretStore.hasApiKey()) {
            apiKeyLayout.setHint(R.string.provider_api_key_saved);
        }

        providerToggle.check(configStore.isCustomActive()
                ? R.id.btn_provider_custom
                : R.id.btn_provider_official);
        updateActiveStatus();
    }

    private void activateOfficialProvider() {
        configStore.activateOfficial();
        applyConfigurationChange();
        updateActiveStatus();
        UiFeedback.show(rootView, getString(R.string.official_provider_enabled));
    }

    private void testCustomProvider(boolean activateAfterSuccess) {
        String endpoint = textOf(endpointInput);
        String model = textOf(modelInput);
        ReminderProviderConfig.AuthType authType =
                authToggle.getCheckedButtonId() == R.id.btn_provider_auth_none
                        ? ReminderProviderConfig.AuthType.NONE
                        : ReminderProviderConfig.AuthType.BEARER;
        String apiKey;
        try {
            apiKey = resolveApiKey(authType);
        } catch (GeneralSecurityException e) {
            UiFeedback.showError(rootView, getString(R.string.provider_key_read_failed));
            return;
        }

        ReminderProviderConfig draft = new ReminderProviderConfig(
                ReminderProviderConfig.CUSTOM_PROFILE_ID,
                ReminderProviderConfig.ProviderType.OPENAI_COMPATIBLE,
                endpoint,
                model,
                authType,
                selectedRefreshInterval,
                configStore.getCustomConfig().getConfigRevision() + 1,
                0);
        String validationError = ReminderProviderConfigValidator.validate(draft, apiKey);
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
            if (draft.getAuthType() == ReminderProviderConfig.AuthType.BEARER) {
                secretStore.saveApiKey(apiKey.trim());
            } else {
                secretStore.deleteApiKey();
            }
            configStore.saveAndActivateCustom(
                    draft.getEndpointUrl(),
                    draft.getModel(),
                    draft.getAuthType(),
                    draft.getRefreshIntervalMinutes(),
                    System.currentTimeMillis());
            apiKeyInput.setText("");
            apiKeyLayout.setHint(draft.getAuthType() == ReminderProviderConfig.AuthType.BEARER
                    ? getString(R.string.provider_api_key_saved)
                    : getString(R.string.provider_api_key));
            applyConfigurationChange();
            updateActiveStatus();
            UiFeedback.show(rootView, getString(R.string.custom_provider_enabled));
        } catch (GeneralSecurityException e) {
            UiFeedback.showError(rootView, getString(R.string.provider_key_save_failed));
        }
    }

    private String resolveApiKey(ReminderProviderConfig.AuthType authType)
            throws GeneralSecurityException {
        if (authType == ReminderProviderConfig.AuthType.NONE) {
            return null;
        }
        String enteredKey = textOf(apiKeyInput);
        return enteredKey.isEmpty() ? secretStore.getApiKey() : enteredKey;
    }

    private void showValidationError(String message) {
        if (message.contains("地址")) {
            UiFeedback.showInputError(endpointLayout, endpointInput, message);
        } else if (message.contains("模型")) {
            UiFeedback.showInputError(modelLayout, modelInput, message);
        } else if (message.contains("API Key")) {
            UiFeedback.showInputError(apiKeyLayout, apiKeyInput, message);
        } else {
            UiFeedback.showError(rootView, message);
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
                : getString(R.string.provider_active_custom, active.getModel()));
    }

    private void setBusy(boolean busy) {
        progressIndicator.setVisibility(busy ? View.VISIBLE : View.GONE);
        testButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        setToggleEnabled(providerToggle, !busy);
        setToggleEnabled(authToggle, !busy);
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
        return 2;
    }

    private static void setToggleEnabled(MaterialButtonToggleGroup group, boolean enabled) {
        group.setEnabled(enabled);
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setEnabled(enabled);
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
