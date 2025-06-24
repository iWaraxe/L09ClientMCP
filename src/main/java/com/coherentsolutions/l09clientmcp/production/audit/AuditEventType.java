package com.coherentsolutions.l09clientmcp.production.audit;

public enum AuditEventType {
    MCP_CONNECTION_ESTABLISHED("mcp.connection.established"),
    MCP_CONNECTION_FAILED("mcp.connection.failed"),
    MCP_CONNECTION_CLOSED("mcp.connection.closed"),
    MCP_TOOL_INVOCATION("mcp.tool.invocation"),
    MCP_TOOL_SUCCESS("mcp.tool.success"),
    MCP_TOOL_FAILURE("mcp.tool.failure"),
    CHAT_REQUEST("chat.request"),
    CHAT_RESPONSE("chat.response"),
    AUTHENTICATION_SUCCESS("auth.success"),
    AUTHENTICATION_FAILURE("auth.failure"),
    AUTHORIZATION_DENIED("auth.denied"),
    RATE_LIMIT_EXCEEDED("ratelimit.exceeded"),
    SECURITY_VIOLATION("security.violation"),
    SYSTEM_ERROR("system.error");

    private final String eventType;

    AuditEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventType() {
        return eventType;
    }
}