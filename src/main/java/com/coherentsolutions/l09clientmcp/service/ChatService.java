package com.coherentsolutions.l09clientmcp.service;

import com.coherentsolutions.l09clientmcp.function.EchoFunction;
import com.coherentsolutions.l09clientmcp.function.PingFunction;
import com.coherentsolutions.l09clientmcp.function.SearchFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatClient chatClient;
    private final McpClientService mcpClientService;
    private final EchoFunction echoFunction;
    private final PingFunction pingFunction;
    private final SearchFunction searchFunction;
    private final AppleScriptService appleScriptService;

    public String chat(String userMessage) {
        log.debug("Processing chat message: {}", userMessage);
        
        // Build system message with MCP awareness
        String systemMessageContent = buildSystemMessage();
        
        var systemMessage = new SystemMessage(systemMessageContent);
        var userMsg = new UserMessage(userMessage);
        
        var prompt = new Prompt(List.of(systemMessage, userMsg));
        
        try {
            // Check if user message suggests tool usage before calling AI
            String toolEnhancedResponse = checkAndExecuteTools(userMessage);
            if (toolEnhancedResponse != null) {
                log.debug("Returning tool-enhanced response");
                return toolEnhancedResponse;
            }
            
            // Standard AI response without tool usage
            var response = chatClient.prompt(prompt).call().content();
            log.debug("Generated standard response: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Error generating chat response", e);
            return "I'm sorry, I encountered an error processing your request.";
        }
    }
    
    /**
     * Build system message with MCP tool awareness.
     * Branch 8 Update: Added Brave Search capabilities for current information access.
     */
    private String buildSystemMessage() {
        StringBuilder systemMessage = new StringBuilder();
        
        systemMessage.append("You are a helpful AI assistant with access to real-time web search capabilities. ");
        systemMessage.append("Be concise and informative. ");
        systemMessage.append("Note: Your knowledge has a cutoff date, but you now have access to web search tools ");
        systemMessage.append("to provide current information about recent developments, software versions, news, and events.");
        
        // Add MCP tool capabilities to system message
        if (mcpClientService.isEnabled() && mcpClientService.isHealthy()) {
            int toolCount = mcpClientService.listTools().size();
            log.debug("MCP is enabled and healthy with {} tools available", toolCount);
            
            systemMessage.append("\n\nIMPORTANT: You have access to powerful external tools:");
            systemMessage.append("\n- Search Tool: Can search the web for current information, news, research papers, documentation");
            systemMessage.append("\n- Echo Tool: Can echo back text with optional formatting (uppercase, lowercase, reverse)");
            systemMessage.append("\n- Ping Tool: Can test connectivity and get server timestamps");
            
            // Add AppleScript capabilities if available
            if (appleScriptService.isAppleScriptAvailable()) {
                systemMessage.append("\n- AppleScript Tool: Can control macOS system functions, get battery status, show notifications, control volume, open applications");
                systemMessage.append("\n- Filesystem Tool: Can read/write files and browse directories on the local system");
            }
            
            systemMessage.append("\n\nWhen users ask about:");
            systemMessage.append("\n• Current events, recent news, or developments");
            systemMessage.append("\n• Latest software versions, updates, or releases");
            systemMessage.append("\n• Recent research findings or technical documentation");
            systemMessage.append("\n• Market information, stock prices, or business news");
            systemMessage.append("\n• Weather, sports scores, or time-sensitive information");
            systemMessage.append("\nUSE THE SEARCH TOOL to provide accurate, up-to-date information rather than relying on your training data.");
            
            systemMessage.append("\n\nFor search queries, use appropriate search types:");
            systemMessage.append("\n- 'web' for general information and documentation");
            systemMessage.append("\n- 'news' for current events and recent developments");
            systemMessage.append("\n- 'images' for visual content");
            systemMessage.append("\n- 'videos' for tutorials and demonstrations");
            
            systemMessage.append("\n\nAlways explain what tool you used and cite your sources with URLs when providing searched information.");
        } else if (mcpClientService.isEnabled()) {
            log.debug("MCP is enabled but not healthy - tools unavailable");
            systemMessage.append("\n\nNote: External tools are configured but currently unavailable. ");
            systemMessage.append("Provide explanations based on your training data and acknowledge limitations for recent information.");
        }
        
        return systemMessage.toString();
    }
    
    /**
     * Check if user message suggests MCP tool usage and execute if appropriate.
     * Branch 8 Implementation: Added search tool detection for current information requests.
     * 
     * @param userMessage The user's input message
     * @return Tool-enhanced response if tool was used, null if no tool needed
     */
    private String checkAndExecuteTools(String userMessage) {
        if (!mcpClientService.isEnabled() || !mcpClientService.isHealthy()) {
            return null;
        }
        
        String lowerMessage = userMessage.toLowerCase();
        
        // Detect search requests for current information
        if (isSearchRequest(lowerMessage)) {
            return handleSearchRequest(userMessage);
        }
        
        // Detect echo tool usage
        if (lowerMessage.contains("echo") && (lowerMessage.contains("uppercase") || 
            lowerMessage.contains("lowercase") || lowerMessage.contains("reverse"))) {
            return handleEchoRequest(userMessage);
        }
        
        // Detect basic echo requests
        if (lowerMessage.startsWith("echo ") || lowerMessage.contains("echo back")) {
            return handleEchoRequest(userMessage);
        }
        
        // Detect ping requests
        if (lowerMessage.contains("ping") || lowerMessage.contains("test connection")) {
            return handlePingRequest(userMessage);
        }
        
        // Detect AppleScript requests
        if (isAppleScriptRequest(lowerMessage)) {
            return handleAppleScriptRequest(userMessage);
        }
        
        return null; // No tool usage detected
    }
    
    /**
     * Determine if user message is requesting current/recent information that requires search.
     */
    private boolean isSearchRequest(String lowerMessage) {
        // Keywords indicating need for current information
        String[] currentInfoKeywords = {
            "current", "recent", "latest", "new", "today", "yesterday", "this week", "this month",
            "what's happening", "what happened", "breaking news", "update", "version", "release"
        };
        
        String[] searchKeywords = {
            "search for", "find", "look up", "tell me about", "what is", "who is", "when did",
            "how to", "where is", "why is", "research", "information about"
        };
        
        // Check for explicit current information requests
        for (String keyword : currentInfoKeywords) {
            if (lowerMessage.contains(keyword)) {
                return true;
            }
        }
        
        // Check for search-style questions about topics that might need current info
        for (String keyword : searchKeywords) {
            if (lowerMessage.contains(keyword)) {
                // Additional check for topics that likely need current information
                if (lowerMessage.contains("price") || lowerMessage.contains("news") ||
                    lowerMessage.contains("weather") || lowerMessage.contains("stock") ||
                    lowerMessage.contains("covid") || lowerMessage.contains("election") ||
                    lowerMessage.contains("technology") || lowerMessage.contains("software")) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Handle search requests using the Brave Search tool.
     */
    private String handleSearchRequest(String userMessage) {
        try {
            log.info("Detected search request in user message: {}", userMessage);
            
            // Extract search query and parameters from user input
            String searchQuery = extractSearchQuery(userMessage);
            String searchType = determineSearchType(userMessage);
            
            // Create search function request
            SearchFunction.Request request = new SearchFunction.Request(
                searchQuery, 
                5,  // Default to 5 results for chat responses
                searchType, 
                "US",  // Default country
                "moderate"  // Default safe search
            );
            
            SearchFunction.Response response = searchFunction.apply(request);
            
            if (response.success() && !response.results().isEmpty()) {
                return formatSearchResponse(response, userMessage);
            } else if (response.success() && response.results().isEmpty()) {
                return String.format("I searched for '%s' but didn't find any relevant results. " +
                    "You might want to try rephrasing your query or being more specific.", searchQuery);
            } else {
                return "I tried to search for current information, but the search service encountered an error: " + 
                       response.error();
            }
        } catch (Exception e) {
            log.error("Error handling search request", e);
            return "I attempted to search for current information but encountered an error: " + e.getMessage();
        }
    }
    
    /**
     * Extract search query from user message.
     */
    private String extractSearchQuery(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();
        
        // Remove common search prefixes and get the core query
        String query = userMessage;
        
        // Common patterns to remove
        String[] prefixesToRemove = {
            "search for ", "find ", "look up ", "tell me about ", "what is ", 
            "who is ", "when did ", "how to ", "where is ", "why is ",
            "what's the latest ", "what are the recent ", "current ", "latest "
        };
        
        for (String prefix : prefixesToRemove) {
            if (lowerMessage.startsWith(prefix)) {
                query = userMessage.substring(prefix.length()).trim();
                break;
            } else if (lowerMessage.contains(prefix)) {
                int index = lowerMessage.indexOf(prefix);
                query = userMessage.substring(index + prefix.length()).trim();
                break;
            }
        }
        
        // Clean up the query
        query = query.replaceAll("[?!.]$", "").trim();
        
        return query.isEmpty() ? userMessage : query;
    }
    
    /**
     * Determine the appropriate search type based on user message.
     */
    private String determineSearchType(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();
        
        if (lowerMessage.contains("news") || lowerMessage.contains("breaking") || 
            lowerMessage.contains("headline") || lowerMessage.contains("recent event")) {
            return "news";
        } else if (lowerMessage.contains("image") || lowerMessage.contains("photo") || 
                   lowerMessage.contains("picture")) {
            return "images";
        } else if (lowerMessage.contains("video") || lowerMessage.contains("tutorial") || 
                   lowerMessage.contains("demonstration")) {
            return "videos";
        } else {
            return "web";  // Default to web search
        }
    }
    
    /**
     * Format search results into a readable response.
     */
    private String formatSearchResponse(SearchFunction.Response response, String originalQuery) {
        StringBuilder result = new StringBuilder();
        
        result.append(String.format("I searched for '%s' and found %d relevant results:\n\n", 
                     response.query(), response.resultCount()));
        
        // Add top results
        int maxResults = Math.min(3, response.results().size()); // Show top 3 results
        for (int i = 0; i < maxResults; i++) {
            SearchFunction.SearchResult searchResult = response.results().get(i);
            result.append(String.format("**%d. %s**\n", i + 1, searchResult.title()));
            result.append(String.format("*Source: %s*\n", searchResult.displayUrl()));
            result.append(String.format("%s\n", searchResult.description()));
            result.append(String.format("🔗 [Read more](%s)\n\n", searchResult.url()));
        }
        
        // Add metadata
        if (response.metadata() != null) {
            result.append(String.format("*Search completed in %dms • %d total results found • %s search*\n", 
                         response.metadata().searchTime(), 
                         response.metadata().totalResults(),
                         response.metadata().searchType()));
        }
        
        result.append("\n💡 *This information was retrieved using live web search to provide current data.*");
        
        return result.toString();
    }
    
    /**
     * Handle echo tool requests with message extraction and formatting.
     */
    private String handleEchoRequest(String userMessage) {
        try {
            log.info("Detected echo request in user message: {}", userMessage);
            
            // Extract message and format from user input
            String messageToEcho = extractEchoMessage(userMessage);
            String format = extractEchoFormat(userMessage);
            
            // Create echo function request
            EchoFunction.Request request = new EchoFunction.Request(messageToEcho, format);
            EchoFunction.Response response = echoFunction.apply(request);
            
            if (response.success()) {
                return String.format("I used the echo tool to process your message:\n\n" +
                    "**Result:** %s\n" +
                    "**Original:** %s\n" +
                    "**Format Applied:** %s", 
                    response.result(), response.originalMessage(), response.formatApplied());
            } else {
                return "I tried to use the echo tool, but it encountered an error: " + response.error();
            }
        } catch (Exception e) {
            log.error("Error handling echo request", e);
            return "I attempted to use the echo tool but encountered an error: " + e.getMessage();
        }
    }
    
    /**
     * Handle ping tool requests.
     */
    private String handlePingRequest(String userMessage) {
        try {
            log.info("Detected ping request in user message: {}", userMessage);
            
            PingFunction.Request request = new PingFunction.Request();
            PingFunction.Response response = pingFunction.apply(request);
            
            if (response.success()) {
                return String.format("I used the ping tool to test MCP connectivity:\n\n" +
                    "**Status:** %s\n" +
                    "**Server Type:** %s\n" +
                    "**Timestamp:** %s\n\n" +
                    "The MCP connection is working properly!",
                    response.message(), response.serverType(), response.timestamp());
            } else {
                return "I tried to ping the MCP server, but it's not responding: " + response.error();
            }
        } catch (Exception e) {
            log.error("Error handling ping request", e);
            return "I attempted to ping the MCP server but encountered an error: " + e.getMessage();
        }
    }
    
    /**
     * Extract the message to echo from user input.
     */
    private String extractEchoMessage(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();
        
        // Look for quoted strings first
        if (userMessage.contains("\"")) {
            int start = userMessage.indexOf("\"");
            int end = userMessage.lastIndexOf("\"");
            if (start != end && start >= 0) {
                return userMessage.substring(start + 1, end);
            }
        }
        
        // Look for "echo " pattern
        if (lowerMessage.startsWith("echo ")) {
            String remainder = userMessage.substring(5).trim();
            // Remove format instructions
            remainder = remainder.replaceAll("(?i)\\s+(in\\s+)?(uppercase|lowercase|reverse)", "").trim();
            return remainder;
        }
        
        // Look for "echo back" pattern
        if (lowerMessage.contains("echo back")) {
            int echoIndex = lowerMessage.indexOf("echo back");
            String remainder = userMessage.substring(echoIndex + 9).trim();
            remainder = remainder.replaceAll("(?i)\\s+(in\\s+)?(uppercase|lowercase|reverse)", "").trim();
            return remainder;
        }
        
        // Default fallback
        return "Hello World";
    }
    
    /**
     * Extract formatting instruction from user input.
     */
    private String extractEchoFormat(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();
        
        if (lowerMessage.contains("uppercase")) {
            return "uppercase";
        } else if (lowerMessage.contains("lowercase")) {
            return "lowercase";
        } else if (lowerMessage.contains("reverse")) {
            return "reverse";
        }
        
        return null; // No format specified
    }
    
    /**
     * Get multi-server status and statistics for user information.
     * Branch 9: Added multi-server awareness and load balancing information.
     */
    public String getMultiServerStatus() {
        if (!mcpClientService.isEnabled()) {
            return "Multi-server MCP functionality is not enabled.";
        }
        
        // Check if this is the multi-server implementation
        if (mcpClientService instanceof MultiServerMcpClientService multiServerService) {
            MultiServerMcpClientService.MultiServerStats stats = multiServerService.getMultiServerStats();
            
            StringBuilder status = new StringBuilder();
            status.append("**Multi-Server MCP Status**\n\n");
            status.append(String.format("• **Total Servers**: %d\n", stats.totalServers()));
            status.append(String.format("• **Healthy Servers**: %d\n", stats.healthyServers()));
            status.append(String.format("• **Available Tools**: %d\n", stats.availableTools()));
            
            if (!stats.serverSelectionCounts().isEmpty()) {
                status.append("\n**Server Load Distribution**:\n");
                stats.serverSelectionCounts().forEach((serverId, count) -> {
                    status.append(String.format("  - %s: %d requests\n", serverId, count));
                });
            }
            
            if (!stats.circuitBreakerStats().isEmpty()) {
                status.append("\n**Circuit Breaker Status**:\n");
                stats.circuitBreakerStats().forEach((serverId, cbStats) -> {
                    status.append(String.format("  - %s: %s (Success: %.1f%%)\n", 
                                 serverId, cbStats.state(), cbStats.getSuccessRate() * 100));
                });
            }
            
            return status.toString();
        } else {
            return "Single-server MCP mode: " + mcpClientService.getStatus();
        }
    }
    
    /**
     * Demonstrate multi-server load balancing by executing the same tool multiple times.
     * Branch 9: Show how requests are distributed across multiple servers.
     */
    public String demonstrateLoadBalancing(String toolName, int requests) {
        if (!mcpClientService.isEnabled()) {
            return "MCP is not enabled, cannot demonstrate load balancing.";
        }
        
        StringBuilder result = new StringBuilder();
        result.append(String.format("**Load Balancing Demonstration**\n\n"));
        result.append(String.format("Executing '%s' tool %d times to show server distribution:\n\n", toolName, requests));
        
        for (int i = 1; i <= requests; i++) {
            try {
                switch (toolName.toLowerCase()) {
                    case "echo" -> {
                        EchoFunction.Request request = new EchoFunction.Request("Test " + i, null);
                        EchoFunction.Response response = echoFunction.apply(request);
                        result.append(String.format("%d. Echo result: %s\n", i, response.result()));
                    }
                    case "ping" -> {
                        PingFunction.Request request = new PingFunction.Request();
                        PingFunction.Response response = pingFunction.apply(request);
                        result.append(String.format("%d. Ping result: %s\n", i, response.message()));
                    }
                    default -> {
                        result.append(String.format("%d. Unsupported tool: %s\n", i, toolName));
                    }
                }
            } catch (Exception e) {
                result.append(String.format("%d. Error: %s\n", i, e.getMessage()));
            }
        }
        
        // Show final server distribution if multi-server
        if (mcpClientService instanceof MultiServerMcpClientService multiServerService) {
            MultiServerMcpClientService.MultiServerStats stats = multiServerService.getMultiServerStats();
            result.append("\n**Final Server Distribution**:\n");
            stats.serverSelectionCounts().forEach((serverId, count) -> {
                result.append(String.format("  - %s: %d requests\n", serverId, count));
            });
        }
        
        return result.toString();
    }
    
    /**
     * Test server health and failover capabilities.
     * Branch 9: Demonstrate health monitoring and circuit breaker functionality.
     */
    public String testServerHealth() {
        if (!mcpClientService.isEnabled()) {
            return "MCP is not enabled, cannot test server health.";
        }
        
        StringBuilder result = new StringBuilder();
        result.append("**Server Health Check Results**\n\n");
        
        // Force health check if multi-server
        if (mcpClientService instanceof MultiServerMcpClientService multiServerService) {
            multiServerService.forceHealthCheck();
            
            // Wait a moment for health checks to complete
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            MultiServerMcpClientService.MultiServerStats stats = multiServerService.getMultiServerStats();
            
            result.append(String.format("Overall Health: %d/%d servers healthy\n\n", 
                         stats.healthyServers(), stats.totalServers()));
            
            if (!stats.circuitBreakerStats().isEmpty()) {
                result.append("**Individual Server Status**:\n");
                stats.circuitBreakerStats().forEach((serverId, cbStats) -> {
                    result.append(String.format("• **%s**: %s\n", serverId, cbStats.state()));
                    result.append(String.format("  - Total calls: %d\n", cbStats.totalCalls()));
                    result.append(String.format("  - Success rate: %.1f%%\n", cbStats.getSuccessRate() * 100));
                    result.append(String.format("  - Consecutive failures: %d\n\n", cbStats.consecutiveFailures()));
                });
            }
        } else {
            result.append("Single-server mode: ").append(mcpClientService.getStatus());
        }
        
        return result.toString();
    }
    
    /**
     * Determine if user message is requesting AppleScript functionality.
     */
    private boolean isAppleScriptRequest(String lowerMessage) {
        // Keywords that suggest AppleScript/system control requests
        String[] appleScriptKeywords = {
            "battery", "battery status", "show notification", "notification", "volume", "set volume",
            "get volume", "system info", "system information", "open app", "open application",
            "battery level", "system status", "mac", "macos", "applescript"
        };
        
        for (String keyword : appleScriptKeywords) {
            if (lowerMessage.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Handle AppleScript requests by determining the specific operation and delegating to AppleScriptService.
     */
    private String handleAppleScriptRequest(String userMessage) {
        if (!appleScriptService.isAppleScriptAvailable()) {
            return "I'd love to help with system control, but AppleScript MCP is not available. " +
                   "Make sure you're running on macOS and the AppleScript MCP server is configured.";
        }
        
        try {
            String lowerMessage = userMessage.toLowerCase();
            
            // Battery status requests
            if (lowerMessage.contains("battery")) {
                String result = appleScriptService.getBatteryStatus();
                return "I checked your Mac's battery status:\n\n" + result;
            }
            
            // Notification requests
            if (lowerMessage.contains("notification") || lowerMessage.contains("notify")) {
                String title = extractNotificationTitle(userMessage);
                String message = extractNotificationMessage(userMessage);
                String result = appleScriptService.showNotification(title, message);
                return "I sent a notification to your Mac:\n\n" + result;
            }
            
            // Volume control requests
            if (lowerMessage.contains("volume")) {
                if (lowerMessage.contains("set") || lowerMessage.contains("change")) {
                    int volume = extractVolumeLevel(userMessage);
                    String result = appleScriptService.setVolume(volume);
                    return "I adjusted your Mac's volume:\n\n" + result;
                } else {
                    String result = appleScriptService.getVolume();
                    return "I checked your Mac's volume level:\n\n" + result;
                }
            }
            
            // System info requests
            if (lowerMessage.contains("system info") || lowerMessage.contains("system information")) {
                String result = appleScriptService.getSystemInfo();
                return "I gathered your Mac's system information:\n\n" + result;
            }
            
            // Application opening requests
            if (lowerMessage.contains("open app") || lowerMessage.contains("open application")) {
                String appName = extractApplicationName(userMessage);
                String result = appleScriptService.openApplication(appName);
                return "I tried to open the application:\n\n" + result;
            }
            
            // Generic AppleScript mention
            return "I can help you control your Mac using AppleScript! I can:\n" +
                   "• Check battery status\n" +
                   "• Show notifications\n" +
                   "• Control volume\n" +
                   "• Get system information\n" +
                   "• Open applications\n\n" +
                   "Just ask me something like 'What's my battery status?' or 'Show me a notification'";
            
        } catch (Exception e) {
            log.error("Error handling AppleScript request", e);
            return "I encountered an error while trying to control your Mac: " + e.getMessage();
        }
    }
    
    private String extractNotificationTitle(String userMessage) {
        // Try to extract title from patterns like "show notification 'title' 'message'"
        if (userMessage.contains("'") || userMessage.contains("\"")) {
            // Handle quoted strings
            String[] parts = userMessage.split("['\"]");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return "Spring AI MCP Demo";
    }
    
    private String extractNotificationMessage(String userMessage) {
        // Try to extract message from patterns
        if (userMessage.contains("'") || userMessage.contains("\"")) {
            String[] parts = userMessage.split("['\"]");
            if (parts.length >= 4) {
                return parts[3];
            } else if (parts.length >= 2) {
                return parts[1];
            }
        }
        
        // Extract text after "notification" keyword
        String lowerMessage = userMessage.toLowerCase();
        int notifyIndex = lowerMessage.indexOf("notification");
        if (notifyIndex >= 0) {
            String remainder = userMessage.substring(notifyIndex + 12).trim();
            if (!remainder.isEmpty()) {
                return remainder;
            }
        }
        
        return "Hello from Spring AI MCP!";
    }
    
    private int extractVolumeLevel(String userMessage) {
        // Try to extract number from the message
        String[] words = userMessage.split("\\s+");
        for (String word : words) {
            try {
                int volume = Integer.parseInt(word.replaceAll("[^0-9]", ""));
                return Math.max(0, Math.min(100, volume));
            } catch (NumberFormatException e) {
                // Continue looking
            }
        }
        return 50; // Default volume
    }
    
    private String extractApplicationName(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();
        
        // Common applications
        if (lowerMessage.contains("finder")) return "Finder";
        if (lowerMessage.contains("safari")) return "Safari";
        if (lowerMessage.contains("chrome")) return "Google Chrome";
        if (lowerMessage.contains("firefox")) return "Firefox";
        if (lowerMessage.contains("terminal")) return "Terminal";
        if (lowerMessage.contains("calculator")) return "Calculator";
        if (lowerMessage.contains("calendar")) return "Calendar";
        if (lowerMessage.contains("mail")) return "Mail";
        if (lowerMessage.contains("notes")) return "Notes";
        if (lowerMessage.contains("music")) return "Music";
        if (lowerMessage.contains("photos")) return "Photos";
        
        // Try to extract app name after "open"
        int openIndex = lowerMessage.indexOf("open");
        if (openIndex >= 0) {
            String remainder = userMessage.substring(openIndex + 4).trim();
            if (remainder.startsWith("app ")) {
                remainder = remainder.substring(4).trim();
            }
            if (remainder.startsWith("application ")) {
                remainder = remainder.substring(12).trim();
            }
            if (!remainder.isEmpty()) {
                // Capitalize first letter
                return remainder.substring(0, 1).toUpperCase() + remainder.substring(1);
            }
        }
        
        return "Finder"; // Default application
    }
}