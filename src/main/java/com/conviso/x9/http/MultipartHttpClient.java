package com.conviso.x9.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Minimal multipart/form-data POST helper implementing the GraphQL multipart
 * request spec (an "operations" part, a "map" part and one file part), used
 * to upload evidence attachments. Kept separate from {@link JsonHttpClient}
 * since the wire format (multipart, not a single JSON body) is different.
 */
public final class MultipartHttpClient {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final String CRLF = "\r\n";

    private MultipartHttpClient() {
    }

    public static HttpJsonResponse postGraphqlFileUpload(
        String url,
        Map<String, String> headers,
        String operationsJson,
        String mapJson,
        String filePartName,
        String fileName,
        String contentType,
        byte[] fileBytes
    ) throws IOException {
        String boundary = "----ConvisoX9Boundary" + System.nanoTime();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }

            byte[] body = buildBody(boundary, operationsJson, mapJson, filePartName, fileName, contentType, fileBytes);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            return new HttpJsonResponse(status, readAll(stream));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] buildBody(
        String boundary, String operationsJson, String mapJson, String filePartName, String fileName, String contentType, byte[] fileBytes
    ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeField(out, boundary, "operations", operationsJson);
        writeField(out, boundary, "map", mapJson);
        writeFilePart(out, boundary, filePartName, fileName, contentType, fileBytes);
        out.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write(CRLF.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(ByteArrayOutputStream out, String boundary, String partName, String fileName, String contentType, byte[] fileBytes) throws IOException {
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write((
            "Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + fileName + "\"" + CRLF
                + "Content-Type: " + contentType + CRLF + CRLF
        ).getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write(CRLF.getBytes(StandardCharsets.UTF_8));
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
