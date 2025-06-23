package com.coherentsolutions.l09clientmcp.mcp;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class McpToolResult {
    private boolean success;
    private String content;
    private Map<String, Object> metadata;
}