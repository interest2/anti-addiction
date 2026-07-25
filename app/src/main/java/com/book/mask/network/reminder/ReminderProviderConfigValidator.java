package com.book.mask.network.reminder;

import java.net.URI;

public final class ReminderProviderConfigValidator {
    private static final int MAX_ENDPOINT_LENGTH = 2048;
    private static final int MAX_MODEL_LENGTH = 200;

    private ReminderProviderConfigValidator() {
    }

    public static String validate(ReminderProviderConfig config, String apiKey) {
        if (config == null) {
            return "Provider 配置为空";
        }
        if (config.isOfficial()) {
            return null;
        }

        String endpoint = config.getEndpointUrl() == null
                ? ""
                : config.getEndpointUrl().trim();
        if (endpoint.isEmpty()) {
            return "请输入 Chat Completions 地址";
        }
        if (endpoint.length() > MAX_ENDPOINT_LENGTH) {
            return "接口地址过长";
        }
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return "接口地址必须使用 HTTPS";
            }
            if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
                return "接口地址缺少有效域名";
            }
            if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
                return "接口地址不能包含账号、查询参数或锚点";
            }
        } catch (IllegalArgumentException e) {
            return "接口地址格式不正确";
        }

        String model = config.getModel() == null ? "" : config.getModel().trim();
        if (model.isEmpty()) {
            return "请输入模型名称";
        }
        if (model.length() > MAX_MODEL_LENGTH) {
            return "模型名称过长";
        }
        if (config.getAuthType() == ReminderProviderConfig.AuthType.BEARER
                && (apiKey == null || apiKey.trim().isEmpty())) {
            return "请输入 API Key";
        }
        return null;
    }
}
