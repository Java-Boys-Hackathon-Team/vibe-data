package ru.javaboys.vibe_data.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class ClientLoggerRequestInterceptor implements ClientHttpRequestInterceptor {
    private static final Logger log = LogManager.getLogger(ClientLoggerRequestInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        logRequest(request, body);
        var response = execution.execute(request, body);
        return logResponse(request, response);
    }

    private void logRequest(HttpRequest request, byte[] body) {
        log.info("Request: {} {}", request.getMethod(), request.getURI());
        log.debug(request.getHeaders());
        if (body != null && body.length > 0) {
            log.info("Request body: {}", new String(body, StandardCharsets.UTF_8));
        }
    }

    private ClientHttpResponse logResponse(HttpRequest request,
            ClientHttpResponse response) throws IOException {
        log.info("Response status: {}", response.getStatusCode());
        log.debug(response.getHeaders());

        byte[] responseBody = response.getBody().readAllBytes();
        if (responseBody.length > 0) {
            log.info("Response body: {}",
                    new String(responseBody, StandardCharsets.UTF_8));
        }

        // Return wrapped response to allow reading the body again
        return new BufferingClientHttpResponseWrapper(response, responseBody);
    }
}