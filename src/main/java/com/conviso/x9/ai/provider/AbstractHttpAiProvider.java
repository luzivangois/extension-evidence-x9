package com.conviso.x9.ai.provider;

import com.conviso.x9.ai.AiProvider;
import com.conviso.x9.ai.AiServiceException;
import com.conviso.x9.ai.AiTextUtils;
import com.conviso.x9.http.HttpJsonResponse;
import com.conviso.x9.http.JsonHttpClient;

import java.io.IOException;
import java.util.Map;

/** Shares the HTTP call/error-handling boilerplate across all {@link AiProvider} implementations. */
abstract class AbstractHttpAiProvider implements AiProvider {

    final String postAndReadBody(String url, Map<String, String> headers, String jsonBody) throws AiServiceException {
        HttpJsonResponse response;
        try {
            response = JsonHttpClient.post(url, headers, jsonBody);
        } catch (IOException ex) {
            throw new AiServiceException("Falha de rede ao chamar a IA: " + ex.getMessage(), ex);
        }
        if (!response.isSuccess()) {
            throw new AiServiceException("HTTP " + response.getStatus() + ": " + AiTextUtils.extractApiErrorMessage(response.getBody()));
        }
        return response.getBody();
    }

    static String safe(String value) {
        return value == null ? "" : value;
    }
}
