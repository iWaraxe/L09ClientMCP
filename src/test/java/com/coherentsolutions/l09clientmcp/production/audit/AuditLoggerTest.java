package com.coherentsolutions.l09clientmcp.production.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLoggerTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditLogger auditLogger;

    @Test
    void testLogEvent_BasicParameters() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any(AuditEvent.class))).thenReturn("{\"eventType\":\"test\"}");

        // When
        auditLogger.logEvent(AuditEventType.CHAT_REQUEST, "user123", "session456", "chat", "process", "SUCCESS");

        // Then
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(objectMapper).writeValueAsString(eventCaptor.capture());

        AuditEvent capturedEvent = eventCaptor.getValue();
        assertEquals("chat.request", capturedEvent.getEventType());
        assertEquals("user123", capturedEvent.getUserId());
        assertEquals("session456", capturedEvent.getSessionId());
        assertEquals("chat", capturedEvent.getResource());
        assertEquals("process", capturedEvent.getAction());
        assertEquals("SUCCESS", capturedEvent.getStatus());
        assertNotNull(capturedEvent.getEventId());
        assertNotNull(capturedEvent.getTimestamp());
    }

    @Test
    void testLogEvent_WithDetails() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any(AuditEvent.class))).thenReturn("{\"eventType\":\"test\"}");

        // When
        auditLogger.logEvent(AuditEventType.MCP_TOOL_INVOCATION, "user123", "session456", 
                           "calculator", "add", "SUCCESS", "Operation completed successfully");

        // Then
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(objectMapper).writeValueAsString(eventCaptor.capture());

        AuditEvent capturedEvent = eventCaptor.getValue();
        assertEquals("Operation completed successfully", capturedEvent.getDetails());
    }

    @Test
    void testLogEvent_WithAllParameters() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any(AuditEvent.class))).thenReturn("{\"eventType\":\"test\"}");
        Map<String, Object> metadata = Map.of("key1", "value1", "key2", 42);

        // When
        auditLogger.logEvent(AuditEventType.MCP_TOOL_SUCCESS, "user123", "session456", 
                           "search", "query", "SUCCESS", "Search completed", 
                           "192.168.1.1", "Mozilla/5.0", metadata, 150L);

        // Then
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(objectMapper).writeValueAsString(eventCaptor.capture());

        AuditEvent capturedEvent = eventCaptor.getValue();
        assertEquals("192.168.1.1", capturedEvent.getSourceIp());
        assertEquals("Mozilla/5.0", capturedEvent.getUserAgent());
        assertEquals(metadata, capturedEvent.getMetadata());
        assertEquals(150L, capturedEvent.getDuration());
    }

    @Test
    void testLogFailureEvent() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any(AuditEvent.class))).thenReturn("{\"eventType\":\"test\"}");

        // When
        auditLogger.logFailureEvent(AuditEventType.MCP_TOOL_FAILURE, "user123", "session456", 
                                  "calculator", "divide", "Division by zero error");

        // Then
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(objectMapper).writeValueAsString(eventCaptor.capture());

        AuditEvent capturedEvent = eventCaptor.getValue();
        assertEquals("mcp.tool.failure", capturedEvent.getEventType());
        assertEquals("FAILURE", capturedEvent.getStatus());
        assertEquals("Division by zero error", capturedEvent.getErrorMessage());
    }

    @Test
    void testLogEvent_JsonProcessingException() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any(AuditEvent.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("JSON error") {});

        // When - Should not throw exception
        assertDoesNotThrow(() -> {
            auditLogger.logEvent(AuditEventType.CHAT_REQUEST, "user123", "session456", "chat", "process", "SUCCESS");
        });

        // Then
        verify(objectMapper).writeValueAsString(any(AuditEvent.class));
    }

    @Test
    void testAuditEventFields() {
        // Test that AuditEvent builder works correctly
        Map<String, Object> metadata = Map.of("test", "value");
        
        AuditEvent event = AuditEvent.builder()
                .eventId("event-123")
                .eventType("test.event")
                .userId("user-456")
                .sessionId("session-789")
                .resource("test-resource")
                .action("test-action")
                .status("SUCCESS")
                .details("Test details")
                .sourceIp("127.0.0.1")
                .userAgent("Test-Agent")
                .metadata(metadata)
                .duration(100L)
                .errorMessage("Test error")
                .build();

        assertEquals("event-123", event.getEventId());
        assertEquals("test.event", event.getEventType());
        assertEquals("user-456", event.getUserId());
        assertEquals("session-789", event.getSessionId());
        assertEquals("test-resource", event.getResource());
        assertEquals("test-action", event.getAction());
        assertEquals("SUCCESS", event.getStatus());
        assertEquals("Test details", event.getDetails());
        assertEquals("127.0.0.1", event.getSourceIp());
        assertEquals("Test-Agent", event.getUserAgent());
        assertEquals(metadata, event.getMetadata());
        assertEquals(100L, event.getDuration());
        assertEquals("Test error", event.getErrorMessage());
    }
}