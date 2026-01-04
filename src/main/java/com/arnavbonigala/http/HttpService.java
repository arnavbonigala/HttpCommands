package com.arnavbonigala.http;

import com.arnavbonigala.config.HttpCommandsConfig;
import com.arnavbonigala.Httpcommands;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class HttpService {
    private final HttpClient httpClient;
    private final HttpCommandsConfig config;
    
    // Patterns for private/local IP ranges
    private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
        "^(127\\.|10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.|localhost|0\\.0\\.0\\.0|::1)"
    );
    
    public HttpService(HttpCommandsConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.connectTimeoutMs))
            .build();
    }
    
    public CompletableFuture<HttpResponse<String>> getAsync(String url) {
        return validateAndRequest(url, null, "GET");
    }
    
    public CompletableFuture<HttpResponse<String>> postAsync(String url, String body) {
        return validateAndRequest(url, body, "POST");
    }
    
    private CompletableFuture<HttpResponse<String>> validateAndRequest(String url, String body, String method) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            
            if (host == null) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid URL: missing host"));
            }
            
            // SSRF protection: check for localhost/private IPs
            if (!config.allowLocalTargets && isLocalOrPrivate(host)) {
                return CompletableFuture.failedFuture(
                    new SecurityException("Local/private IP addresses are not allowed. Set allowLocalTargets=true in config to override.")
                );
            }
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(config.requestTimeoutMs));
            
            if ("POST".equals(method)) {
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", config.postContentType);
            } else {
                requestBuilder.GET();
            }
            
            HttpRequest request = requestBuilder.build();
            
            // Log request (without query params for security)
            String logInfo = method + " " + host + (uri.getPath() != null ? uri.getPath() : "");
            Httpcommands.LOGGER.info("HTTP Request: {}", logInfo);
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        Httpcommands.LOGGER.error("HTTP Request failed: {} - {}", logInfo, throwable.getMessage());
                    } else if (config.showStatusCode) {
                        Httpcommands.LOGGER.info("HTTP Response: {} - Status {}", logInfo, response.statusCode());
                    }
                });
                
        } catch (Exception e) {
            Httpcommands.LOGGER.error("Failed to create HTTP request", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private boolean isLocalOrPrivate(String host) {
        if (host == null) return true;
        
        // Check for localhost variants
        if (host.equalsIgnoreCase("localhost") || 
            host.equals("127.0.0.1") || 
            host.equals("::1") ||
            host.equals("0.0.0.0")) {
            return true;
        }
        
        // Check if host matches private IP pattern
        if (PRIVATE_IP_PATTERN.matcher(host).find()) {
            return true;
        }
        
        // Try to resolve and check IP
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || 
                addr.isLinkLocalAddress() || 
                addr.isSiteLocalAddress() ||
                addr.isAnyLocalAddress()) {
                return true;
            }
            
            byte[] bytes = addr.getAddress();
            // Check for private IP ranges
            if (bytes.length == 4) {
                // 10.0.0.0/8
                if (bytes[0] == 10) return true;
                // 172.16.0.0/12
                if (bytes[0] == (byte)172 && bytes[1] >= 16 && bytes[1] <= 31) return true;
                // 192.168.0.0/16
                if (bytes[0] == (byte)192 && bytes[1] == (byte)168) return true;
                // 127.0.0.0/8
                if (bytes[0] == 127) return true;
            }
        } catch (Exception e) {
            // If we can't resolve, be cautious
            Httpcommands.LOGGER.warn("Could not resolve host for SSRF check: {}", host);
        }
        
        return false;
    }
}

