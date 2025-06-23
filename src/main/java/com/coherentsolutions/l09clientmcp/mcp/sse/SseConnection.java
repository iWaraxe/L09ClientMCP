package com.coherentsolutions.l09clientmcp.mcp.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * SSE connection handler for MCP protocol communication.
 * Branch 5: Implements real-time communication with external MCP servers using Server-Sent Events.
 * 
 * Educational Focus:
 * - Real-time communication patterns with SSE
 * - Connection lifecycle management
 * - Asynchronous message handling
 * - Error recovery and reconnection logic
 */
@Slf4j
public class SseConnection {
    
    private final String serverUrl;
    private final int timeoutSeconds;
    private final int retryAttempts;
    private final int retryDelaySeconds;
    private final ExecutorService executorService;
    
    private volatile boolean connected;
    private volatile boolean shouldReconnect;
    private HttpURLConnection connection;
    private BufferedReader reader;
    private Consumer<String> messageHandler;
    private Consumer<Exception> errorHandler;
    
    public SseConnection(String serverUrl, int timeoutSeconds, int retryAttempts, int retryDelaySeconds) {
        this.serverUrl = serverUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.retryAttempts = retryAttempts;
        this.retryDelaySeconds = retryDelaySeconds;
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SSE-Connection-" + extractServerName(serverUrl));
            t.setDaemon(true);
            return t;
        });
        this.connected = false;
        this.shouldReconnect = true;
    }
    
    /**
     * Connect to the SSE endpoint and start listening for messages.
     */
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            int attempts = 0;
            while (attempts < retryAttempts && shouldReconnect) {
                try {
                    attempts++;
                    log.info("Attempting SSE connection to {} (attempt {}/{})", serverUrl, attempts, retryAttempts);
                    
                    establishConnection();
                    connected = true;
                    log.info("SSE connection established successfully to {}", serverUrl);
                    
                    // Start listening for messages
                    listenForMessages();
                    break;
                    
                } catch (Exception e) {
                    log.warn("SSE connection attempt {} failed: {}", attempts, e.getMessage());
                    if (attempts < retryAttempts) {
                        try {
                            Thread.sleep(retryDelaySeconds * 1000L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            if (!connected) {
                log.error("Failed to establish SSE connection after {} attempts", retryAttempts);
                if (errorHandler != null) {
                    errorHandler.accept(new IOException("Connection failed after " + retryAttempts + " attempts"));
                }
            }
        }, executorService);
    }
    
    /**
     * Disconnect from the SSE endpoint.
     */
    public void disconnect() {
        shouldReconnect = false;
        connected = false;
        
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            log.warn("Error closing SSE reader", e);
        }
        
        if (connection != null) {
            connection.disconnect();
        }
        
        executorService.shutdown();
        log.info("SSE connection disconnected from {}", serverUrl);
    }
    
    /**
     * Check if the connection is currently active.
     */
    public boolean isConnected() {
        return connected;
    }
    
    /**
     * Set the message handler for incoming SSE messages.
     */
    public void setMessageHandler(Consumer<String> messageHandler) {
        this.messageHandler = messageHandler;
    }
    
    /**
     * Set the error handler for connection errors.
     */
    public void setErrorHandler(Consumer<Exception> errorHandler) {
        this.errorHandler = errorHandler;
    }
    
    private void establishConnection() throws IOException {
        URL url = new URL(serverUrl);
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + responseCode + ": " + connection.getResponseMessage());
        }
        
        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    }
    
    private void listenForMessages() {
        try {
            String line;
            StringBuilder messageBuilder = new StringBuilder();
            
            while (connected && shouldReconnect && (line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6); // Remove "data: " prefix
                    messageBuilder.append(data);
                } else if (line.isEmpty()) {
                    // Empty line indicates end of message
                    if (messageBuilder.length() > 0) {
                        String message = messageBuilder.toString();
                        messageBuilder.setLength(0);
                        
                        if (messageHandler != null) {
                            try {
                                messageHandler.accept(message);
                            } catch (Exception e) {
                                log.error("Error handling SSE message", e);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (connected) {
                log.error("Error reading SSE messages", e);
                connected = false;
                if (errorHandler != null) {
                    errorHandler.accept(e);
                }
            }
        }
    }
    
    private String extractServerName(String url) {
        try {
            return new URL(url).getHost() + ":" + new URL(url).getPort();
        } catch (Exception e) {
            return "unknown";
        }
    }
}