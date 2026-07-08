package com.conviso.x9.evidence;

/**
 * Best-effort snapshot of a Burp HTTP message, used both for AI prompts and
 * for local requirement-matching heuristics.
 */
public final class HttpEvidence {

    public static final HttpEvidence EMPTY = new HttpEvidence("N/A", "N/A", "N/A", "", "", "", "", "", 0, "");

    private final String method;
    private final String url;
    private final String status;
    private final String requestSnippet;
    private final String responseSnippet;
    private final String fullRequest;
    private final String fullResponse;
    private final String scheme;
    private final int port;
    private final String parameters;

    public HttpEvidence(
        String method, String url, String status, String requestSnippet, String responseSnippet,
        String fullRequest, String fullResponse, String scheme, int port, String parameters
    ) {
        this.method = method;
        this.url = url;
        this.status = status;
        this.requestSnippet = requestSnippet;
        this.responseSnippet = responseSnippet;
        this.fullRequest = fullRequest;
        this.fullResponse = fullResponse;
        this.scheme = scheme;
        this.port = port;
        this.parameters = parameters;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public String getStatus() {
        return status;
    }

    public String getRequestSnippet() {
        return requestSnippet;
    }

    public String getResponseSnippet() {
        return responseSnippet;
    }

    /** Untruncated raw HTTP request text, used for evidence attachments/creation payloads (not for AI prompts). */
    public String getFullRequest() {
        return fullRequest;
    }

    /** Untruncated raw HTTP response text, used for evidence attachments/creation payloads (not for AI prompts). */
    public String getFullResponse() {
        return fullResponse;
    }

    public String getScheme() {
        return scheme;
    }

    public int getPort() {
        return port;
    }

    /** Raw URL query string, e.g. {@code a=1&b=2}, or empty when the request has no query parameters. */
    public String getParameters() {
        return parameters;
    }

    /** Combined text used to match evidence against requirement keywords. */
    public String asSearchableText() {
        return "method=" + method + "\nurl=" + url + "\nstatus=" + status
            + "\nrequest_snippet=" + requestSnippet + "\nresponse_snippet=" + responseSnippet;
    }
}
