package com.book.mask.network.reminder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ProviderHttpClient {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 90_000;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    private final Set<HttpURLConnection> activeConnections = Collections.newSetFromMap(
            new ConcurrentHashMap<>());

    public HttpResponse postJson(
            String endpointUrl,
            Map<String, String> headers,
            String body) throws IOException {
        HttpURLConnection connection = null;
        try {
            byte[] requestBytes = body.getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(endpointUrl).openConnection();
            activeConnections.add(connection);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(requestBytes.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }

            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBytes);
            }

            int statusCode = connection.getResponseCode();
            InputStream input = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody = input == null ? "" : readLimited(input);
            return new HttpResponse(statusCode, responseBody);
        } finally {
            if (connection != null) {
                activeConnections.remove(connection);
                connection.disconnect();
            }
        }
    }

    public void cancelActiveRequests() {
        for (HttpURLConnection connection : activeConnections) {
            connection.disconnect();
        }
        activeConnections.clear();
    }

    private String readLimited(InputStream input) throws IOException {
        try (InputStream closeableInput = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = closeableInput.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("Provider response is too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static final class HttpResponse {
        private final int statusCode;
        private final String body;

        private HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }
    }
}
