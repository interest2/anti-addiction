package com.book.mask.personalize;

import com.tencent.mmkv.MMKV;

import java.net.URI;
import java.net.URISyntaxException;

/** 电脑端 time-divider 状态接口的持久化配置。 */
public final class ComputerFocusSettings {
    public static final String KEY_SERVER_ADDRESS = "computer_focus_server_address";
    public static final String DEFAULT_STATUS_PATH = "/api/time-divider/status";

    private static final String INVALID_ADDRESS_HINT = "请按 IP:端口 格式填写，例如 100.64.0.1:17321";

    private final MMKV storage;

    public ComputerFocusSettings() {
        storage = SettingsStorage.open();
    }

    /** 已保存的地址，形如 {@code 100.64.0.1:17321}；未配置时为空串。 */
    public String getServerAddress() {
        return storage.getString(KEY_SERVER_ADDRESS, "");
    }

    public void setServerAddress(String rawAddress) {
        String address = normalizeServerAddress(rawAddress);
        if (address.isEmpty()) {
            storage.removeValueForKey(KEY_SERVER_ADDRESS);
            return;
        }
        storage.putString(KEY_SERVER_ADDRESS, address).commit();
    }

    public String getStatusEndpoint() {
        String address = getServerAddress();
        if (address.isEmpty()) {
            return "";
        }
        return buildStatusEndpoint(address);
    }

    /**
     * 用户填 {@code IP:端口}，这里补出 {@code http://} 与状态路径。
     * 同时兼容历史上存过的完整 URL 形式。
     */
    private static String buildStatusEndpoint(String storedAddress) {
        String withScheme = storedAddress.matches("(?i)^https?://.*")
                ? storedAddress
                : "http://" + storedAddress;
        try {
            URI uri = URI.create(withScheme);
            if (uri.getPort() == -1) {
                throw new IllegalStateException("电脑任务专注地址缺少端口");
            }
            String path = uri.getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) {
                path = DEFAULT_STATUS_PATH;
            }
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    path,
                    uri.getQuery(),
                    null
            ).toString();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("电脑任务专注地址无效", e);
        }
    }

    /**
     * 归一化用户输入：要求 {@code IP:端口}，去掉误粘贴的 {@code http://} 前缀和末尾斜杠。
     * 空输入返回空串表示关闭功能；缺端口 / 带路径 / 带账号 / 带查询串等非法形态抛
     * {@link IllegalArgumentException}。
     */
    public static String normalizeServerAddress(String rawAddress) {
        String address = rawAddress == null ? "" : rawAddress.trim();
        if (address.isEmpty()) {
            return "";
        }
        address = address.replaceFirst("(?i)^https?://", "").replaceAll("/+$", "");
        if (address.isEmpty()
                || address.contains("/")
                || address.contains("@")
                || address.contains("?")
                || address.contains("#")
                || address.contains(" ")) {
            throw new IllegalArgumentException(INVALID_ADDRESS_HINT);
        }

        String host;
        int port;
        try {
            URI uri = new URI("http://" + address);
            host = uri.getHost();
            port = uri.getPort();
            boolean hasPath = uri.getPath() != null && !uri.getPath().isEmpty();
            if (host == null || host.isEmpty() || hasPath) {
                throw new IllegalArgumentException(INVALID_ADDRESS_HINT);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(INVALID_ADDRESS_HINT, e);
        }

        if (port == -1) {
            throw new IllegalArgumentException("请连端口一起填写，格式为 IP:端口，例如 100.64.0.1:17321");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("端口号无效");
        }
        return host + ":" + port;
    }
}
