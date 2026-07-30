package com.book.mask.network.reminder.config;

import java.net.URI;

public final class ReminderProviderConfigValidator {
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_ENDPOINT_LENGTH = 2048;
    private static final int MAX_MODEL_LENGTH = 200;

    public enum Error {
        EMPTY_CONFIG("Provider 配置为空"),
        EMPTY_NAME("请输入名称"),
        NAME_TOO_LONG("名称过长"),
        EMPTY_ENDPOINT("请输入接口地址"),
        ENDPOINT_TOO_LONG("接口地址过长"),
        ENDPOINT_REQUIRES_HTTPS("接口地址必须使用 HTTPS"),
        ENDPOINT_MISSING_HOST("接口地址缺少有效域名"),
        ENDPOINT_HAS_UNSUPPORTED_PARTS("接口地址不能包含账号、查询参数或锚点"),
        INVALID_ENDPOINT("接口地址格式不正确"),
        EMPTY_MODEL("请输入指定模型"),
        MODEL_TOO_LONG("指定模型过长"),
        EMPTY_API_KEY("请输入 API Key");

        private final String message;

        Error(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private ReminderProviderConfigValidator() {
    }

    public static Error validate(ReminderProviderConfig config, String apiKey) {
        if (config == null) {
            return Error.EMPTY_CONFIG;
        }
        if (config.isOfficial()) {
            return null;
        }

        String providerName = config.getProviderName() == null
                ? ""
                : config.getProviderName().trim();
        if (providerName.isEmpty()) {
            return Error.EMPTY_NAME;
        }
        if (providerName.length() > MAX_NAME_LENGTH) {
            return Error.NAME_TOO_LONG;
        }

        String endpoint = config.getEndpointUrl() == null
                ? ""
                : config.getEndpointUrl().trim();
        if (endpoint.isEmpty()) {
            return Error.EMPTY_ENDPOINT;
        }
        if (endpoint.length() > MAX_ENDPOINT_LENGTH) {
            return Error.ENDPOINT_TOO_LONG;
        }
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return Error.ENDPOINT_REQUIRES_HTTPS;
            }
            if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
                return Error.ENDPOINT_MISSING_HOST;
            }
            if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
                return Error.ENDPOINT_HAS_UNSUPPORTED_PARTS;
            }
        } catch (IllegalArgumentException e) {
            return Error.INVALID_ENDPOINT;
        }

        String model = config.getModel() == null ? "" : config.getModel().trim();
        if (model.isEmpty()) {
            return Error.EMPTY_MODEL;
        }
        if (model.length() > MAX_MODEL_LENGTH) {
            return Error.MODEL_TOO_LONG;
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Error.EMPTY_API_KEY;
        }
        return null;
    }
}
