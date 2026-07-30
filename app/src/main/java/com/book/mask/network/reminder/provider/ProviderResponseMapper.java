package com.book.mask.network.reminder.provider;

import com.book.mask.network.reminder.ProviderResult;

import java.io.IOException;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLException;

final class ProviderResponseMapper {
    private ProviderResponseMapper() {
    }

    static ProviderResult fromHttpStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return ProviderResult.failure(ProviderResult.ErrorCode.AUTHENTICATION, statusCode);
        }
        if (statusCode == 429) {
            return ProviderResult.failure(ProviderResult.ErrorCode.RATE_LIMIT, statusCode);
        }
        if (statusCode >= 500) {
            return ProviderResult.failure(ProviderResult.ErrorCode.SERVER, statusCode);
        }
        return ProviderResult.failure(ProviderResult.ErrorCode.INVALID_RESPONSE, statusCode);
    }

    static ProviderResult fromException(IOException exception) {
        if (exception instanceof SocketTimeoutException) {
            return ProviderResult.failure(ProviderResult.ErrorCode.TIMEOUT);
        }
        if (exception instanceof SSLException) {
            return ProviderResult.failure(ProviderResult.ErrorCode.NETWORK);
        }
        return ProviderResult.failure(ProviderResult.ErrorCode.NETWORK);
    }
}
