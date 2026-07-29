package com.book.mask.network.reminder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ProviderPreset {
    private final String id;
    private final int nameResId;
    private final String endpointUrl;
    private final List<String> models;

    public ProviderPreset(String id, int nameResId, String endpointUrl, String... models) {
        this.id = id;
        this.nameResId = nameResId;
        this.endpointUrl = endpointUrl;
        this.models = Collections.unmodifiableList(Arrays.asList(models));
    }

    public String getId() {
        return id;
    }

    public int getNameResId() {
        return nameResId;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public List<String> getModels() {
        return models;
    }

    public String getDefaultModel() {
        return models.isEmpty() ? "" : models.get(0);
    }
}
