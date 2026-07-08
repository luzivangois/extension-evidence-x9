package com.conviso.x9.http;

/** Result of a JSON HTTP call: raw status code plus the response body (success or error payload). */
public final class HttpJsonResponse {

    private final int status;
    private final String body;

    public HttpJsonResponse(int status, String body) {
        this.status = status;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
