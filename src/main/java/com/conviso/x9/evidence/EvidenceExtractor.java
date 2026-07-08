package com.conviso.x9.evidence;

import burp.IExtensionHelpers;
import burp.IHttpRequestResponse;
import burp.IHttpService;
import burp.IRequestInfo;
import burp.IResponseInfo;

/**
 * Reads method/URL/status/snippets out of a Burp message. Centralizes logic
 * that was previously duplicated in three places across the old god class.
 */
public final class EvidenceExtractor {

    private static final int SNIPPET_MAX_LENGTH = 1400;

    private final IExtensionHelpers helpers;

    public EvidenceExtractor(IExtensionHelpers helpers) {
        this.helpers = helpers;
    }

    public HttpEvidence extract(IHttpRequestResponse message) {
        if (message == null) {
            return HttpEvidence.EMPTY;
        }

        String method = "N/A";
        String url = "N/A";
        String status = "N/A";
        String requestSnippet = "";
        String responseSnippet = "";
        String fullRequest = "";
        String fullResponse = "";
        String scheme = "";
        int port = 0;
        String parameters = "";

        try {
            byte[] request = message.getRequest();
            if (request != null) {
                IRequestInfo requestInfo = helpers.analyzeRequest(message);
                method = requestInfo.getMethod();
                if (requestInfo.getUrl() != null) {
                    url = requestInfo.getUrl().toString();
                    parameters = safe(requestInfo.getUrl().getQuery());
                }
                fullRequest = safe(helpers.bytesToString(request));
                requestSnippet = truncate(fullRequest);
            }

            byte[] response = message.getResponse();
            if (response != null) {
                IResponseInfo responseInfo = helpers.analyzeResponse(response);
                status = String.valueOf(responseInfo.getStatusCode());
                fullResponse = safe(helpers.bytesToString(response));
                responseSnippet = truncate(fullResponse);
            }

            IHttpService httpService = message.getHttpService();
            if (httpService != null) {
                scheme = safe(httpService.getProtocol()).toUpperCase(java.util.Locale.ROOT);
                port = httpService.getPort();
            }
        } catch (RuntimeException ex) {
            // Best-effort extraction: a malformed message should not break the caller.
        }

        return new HttpEvidence(method, url, status, requestSnippet, responseSnippet, fullRequest, fullResponse, scheme, port, parameters);
    }

    private static String truncate(String value) {
        return value.length() > SNIPPET_MAX_LENGTH ? value.substring(0, SNIPPET_MAX_LENGTH) : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
