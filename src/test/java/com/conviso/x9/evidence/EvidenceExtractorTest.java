package com.conviso.x9.evidence;

import burp.IExtensionHelpers;
import burp.IHttpRequestResponse;
import burp.IHttpService;
import burp.IRequestInfo;
import burp.IResponseInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceExtractorTest {

    @Mock
    private IExtensionHelpers helpers;
    @Mock
    private IHttpRequestResponse message;
    @Mock
    private IRequestInfo requestInfo;
    @Mock
    private IResponseInfo responseInfo;
    @Mock
    private IHttpService httpService;

    @Test
    void returnsEmptyEvidenceForNullMessage() {
        EvidenceExtractor extractor = new EvidenceExtractor(helpers);
        assertEquals(HttpEvidence.EMPTY, extractor.extract(null));
    }

    @Test
    void extractsMethodUrlStatusAndSnippets() throws MalformedURLException {
        byte[] request = "GET /pentest HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] response = "HTTP/1.1 200 OK\r\n\r\nbody".getBytes(StandardCharsets.UTF_8);

        when(message.getRequest()).thenReturn(request);
        when(message.getResponse()).thenReturn(response);
        when(helpers.analyzeRequest(message)).thenReturn(requestInfo);
        when(helpers.analyzeResponse(response)).thenReturn(responseInfo);
        when(requestInfo.getMethod()).thenReturn("GET");
        when(requestInfo.getUrl()).thenReturn(new URL("https://example.test/pentest?debug=1"));
        when(responseInfo.getStatusCode()).thenReturn((short) 200);
        when(message.getHttpService()).thenReturn(httpService);
        when(httpService.getProtocol()).thenReturn("https");
        when(httpService.getPort()).thenReturn(443);
        lenient().when(helpers.bytesToString(any(byte[].class))).thenAnswer(invocation -> new String(invocation.getArgument(0), StandardCharsets.UTF_8));

        HttpEvidence evidence = new EvidenceExtractor(helpers).extract(message);

        assertEquals("GET", evidence.getMethod());
        assertEquals("https://example.test/pentest?debug=1", evidence.getUrl());
        assertEquals("200", evidence.getStatus());
        assertEquals(new String(request, StandardCharsets.UTF_8), evidence.getRequestSnippet());
        assertEquals(new String(response, StandardCharsets.UTF_8), evidence.getResponseSnippet());
        assertEquals(new String(request, StandardCharsets.UTF_8), evidence.getFullRequest());
        assertEquals(new String(response, StandardCharsets.UTF_8), evidence.getFullResponse());
        assertEquals("HTTPS", evidence.getScheme());
        assertEquals(443, evidence.getPort());
        assertEquals("debug=1", evidence.getParameters());
    }

    @Test
    void fallsBackToDefaultsWhenAnalysisThrows() {
        when(message.getRequest()).thenReturn(new byte[]{1, 2, 3});
        when(helpers.analyzeRequest(message)).thenThrow(new RuntimeException("malformed"));

        HttpEvidence evidence = new EvidenceExtractor(helpers).extract(message);

        assertEquals("N/A", evidence.getMethod());
        assertEquals("N/A", evidence.getUrl());
        assertEquals("N/A", evidence.getStatus());
    }
}
