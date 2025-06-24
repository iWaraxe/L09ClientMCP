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
}