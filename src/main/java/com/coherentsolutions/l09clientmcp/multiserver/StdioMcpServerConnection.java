package com.coherentsolutions.l09clientmcp.multiserver;

import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * STDIO-based MCP server connection implementation.
 * 
 * This implementation spawns an external process and communicates via
 * standard input/output streams using JSON-RPC protocol.
 * 
 * Educational Focus:
 * - Process management in Java
 * - Inter-process communication patterns
 * - JSON-RPC protocol implementation
 * - Resource lifecycle management
 */
@Slf4j
public class StdioMcpServerConnection implements McpServerConnection {
    
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int INITIALIZATION_TIMEOUT_SECONDS = 60;
    
    private final String serverId;
    private final String command;
    private final List<String> args;
    private final Map<String, String> environment;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    
    private Process process;
    private BufferedReader processReader;
    private BufferedWriter processWriter;
    private Set<String> availableTools;
    private volatile boolean connected = false;
    private final Instant connectionStartTime = Instant.now();
    
    // Statistics tracking
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    
    public StdioMcpServerConnection(String serverId, String command, List<String> args, Map<String, String> environment) {
        this.serverId = serverId;
        this.command = command;
        this.args = args != null ? new ArrayList<>(args) : new ArrayList<>();
        this.environment = environment != null ? new HashMap<>(environment) : new HashMap<>();
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("stdio-mcp-" + serverId);
            thread.setDaemon(true);
            return thread;
        });
        this.availableTools = new HashSet<>();
    }
    
    @Override
    public String getServerId() {
        return serverId;
    }
    
    @Override
    public String getUrl() {
        return "stdio://" + command + (args.isEmpty() ? "" : " " + String.join(" ", args));
    }
    
    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.STDIO;
    }
    
    @Override
    public boolean isConnected() {
        return connected && process != null && process.isAlive();
    }
    
    @Override
    public void connect() throws McpConnectionException {
        if (isConnected()) {
            log.warn("Already connected to STDIO server: {}", serverId);
            return;
        }
        
        log.info("Connecting to STDIO MCP server: {} with command: {}", serverId, command);
        
        try {
            // Build process
            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(command);
            fullCommand.addAll(args);
            
            ProcessBuilder processBuilder = new ProcessBuilder(fullCommand);
            processBuilder.environment().putAll(environment);
            
            // Start process
            process = processBuilder.start();
            
            // Setup I/O streams
            processReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            processWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            
            // Initialize connection
            initializeConnection();
            
            // Discover available tools
            discoverTools();
            
            connected = true;
            log.info("Successfully connected to STDIO MCP server: {}", serverId);
            
        } catch (IOException e) {
            disconnect();
            throw new McpConnectionException("Failed to start STDIO process for " + serverId, e);
        } catch (Exception e) {
            disconnect();
            throw new McpConnectionException("Failed to connect to STDIO server " + serverId, e);
        }
    }
    
    @Override
    public void disconnect() {
        log.info("Disconnecting from STDIO MCP server: {}", serverId);
        
        connected = false;
        
        // Close streams
        closeQuietly(processWriter);
        closeQuietly(processReader);
        
        // Terminate process
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            process = null;
        }
        
        // Shutdown executor
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
        
        log.info("Disconnected from STDIO MCP server: {}", serverId);
    }
    
    @Override
    public Set<String> getAvailableTools() {
        return new HashSet<>(availableTools);
    }
    
    @Override
    public McpToolResult invokeTool(String toolName, Map<String, Object> arguments) throws McpConnectionException {
        if (!isConnected()) {
            throw new McpConnectionException("Not connected to STDIO server: " + serverId);
        }
        
        if (!availableTools.contains(toolName)) {
            throw new McpConnectionException("Tool not available: " + toolName);
        }
        
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        
        try {
            // Create JSON-RPC request
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("method", "tools/call");
            request.put("id", requestIdCounter.getAndIncrement());
            
            ObjectNode params = request.putObject("params");
            params.put("name", toolName);
            params.set("arguments", objectMapper.valueToTree(arguments != null ? arguments : Map.of()));
            
            // Send request and get response
            String response = sendRequest(request.toString());
            
            // Parse response
            ObjectNode responseNode = (ObjectNode) objectMapper.readTree(response);
            
            if (responseNode.has("error")) {
                failedRequests.incrementAndGet();
                String errorMessage = responseNode.path("error").path("message").asText("Unknown error");
                return McpToolResult.builder()
                        .success(false)
                        .content(errorMessage)
                        .metadata(Map.of(
                            "server_id", serverId,
                            "execution_time_ms", System.currentTimeMillis() - startTime
                        ))
                        .build();
            }
            
            successfulRequests.incrementAndGet();
            String content = responseNode.path("result").path("content").path(0).path("text").asText("");
            
            return McpToolResult.builder()
                    .success(true)
                    .content(content)
                    .metadata(Map.of(
                        "server_id", serverId,
                        "execution_time_ms", System.currentTimeMillis() - startTime
                    ))
                    .build();
            
        } catch (Exception e) {
            failedRequests.incrementAndGet();
            log.error("Failed to invoke tool {} on STDIO server {}", toolName, serverId, e);
            throw new McpConnectionException("Tool invocation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public HealthCheckResult performHealthCheck() {
        long startTime = System.currentTimeMillis();
        
        if (!isConnected()) {
            return new HealthCheckResult(false, 0, "Not connected", System.currentTimeMillis());
        }
        
        try {
            // Send a simple request to check if server responds
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("method", "initialize");
            request.put("id", requestIdCounter.getAndIncrement());
            request.putObject("params")
                    .putObject("clientInfo")
                    .put("name", "spring-ai-mcp-health-check")
                    .put("version", "1.0.0");
            
            sendRequest(request.toString(), 5); // 5 second timeout for health check
            
            long responseTime = System.currentTimeMillis() - startTime;
            return new HealthCheckResult(true, responseTime, "Healthy", System.currentTimeMillis());
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return new HealthCheckResult(false, responseTime, "Health check failed: " + e.getMessage(), System.currentTimeMillis());
        }
    }
    
    @Override
    public ServerInfo getServerInfo() {
        return new ServerInfo(
            serverId,
            "1.0.0",
            "STDIO MCP Server: " + command,
            Set.of("tools", "stdio"),
            Map.of(
                "command", command,
                "args", args,
                "environment_vars", environment.keySet()
            )
        );
    }
    
    @Override
    public ConnectionStats getConnectionStats() {
        long total = totalRequests.get();
        long successful = successfulRequests.get();
        long failed = failedRequests.get();
        long avgResponseTime = total > 0 ? (successful + failed) * 100 / total : 0; // Simplified calculation
        
        return new ConnectionStats(
            total,
            successful,
            failed,
            avgResponseTime,
            System.currentTimeMillis(),
            Duration.between(connectionStartTime, Instant.now())
        );
    }
    
    private void initializeConnection() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        // Send initialization request
        ObjectNode initRequest = objectMapper.createObjectNode();
        initRequest.put("jsonrpc", "2.0");
        initRequest.put("method", "initialize");
        initRequest.put("id", requestIdCounter.getAndIncrement());
        
        ObjectNode params = initRequest.putObject("params");
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", "spring-ai-mcp-client");
        clientInfo.put("version", "1.0.0");
        
        ObjectNode capabilities = params.putObject("capabilities");
        ObjectNode tools = capabilities.putObject("tools");
        tools.put("enabled", true);
        
        String response = sendRequest(initRequest.toString(), INITIALIZATION_TIMEOUT_SECONDS);
        log.debug("Initialization response: {}", response);
    }
    
    private void discoverTools() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        // Send tools/list request
        ObjectNode toolsRequest = objectMapper.createObjectNode();
        toolsRequest.put("jsonrpc", "2.0");
        toolsRequest.put("method", "tools/list");
        toolsRequest.put("id", requestIdCounter.getAndIncrement());
        
        String response = sendRequest(toolsRequest.toString());
        ObjectNode responseNode = (ObjectNode) objectMapper.readTree(response);
        
        availableTools.clear();
        if (responseNode.has("result") && responseNode.get("result").has("tools")) {
            responseNode.get("result").get("tools").forEach(tool -> {
                String toolName = tool.path("name").asText();
                if (!toolName.isEmpty()) {
                    availableTools.add(toolName);
                }
            });
        }
        
        log.info("Discovered {} tools on STDIO server {}: {}", availableTools.size(), serverId, availableTools);
    }
    
    private String sendRequest(String request) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        return sendRequest(request, DEFAULT_TIMEOUT_SECONDS);
    }
    
    private String sendRequest(String request, int timeoutSeconds) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        if (!isConnected()) {
            throw new IOException("Not connected to STDIO server");
        }
        
        CompletableFuture<String> responseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                // Send request
                processWriter.write(request);
                processWriter.newLine();
                processWriter.flush();
                
                // Read response
                String response = processReader.readLine();
                if (response == null) {
                    throw new IOException("Server closed connection");
                }
                
                return response;
                
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executorService);
        
        try {
            return responseFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            responseFuture.cancel(true);
            throw new TimeoutException("Request timed out after " + timeoutSeconds + " seconds");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw e;
        }
    }
    
    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                log.debug("Error closing resource", e);
            }
        }
    }
}