package com.coherentsolutions.l09clientmcp.production.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogger {
    
    private static final String AUDIT_LOGGER_NAME = "AUDIT";
    private final ObjectMapper objectMapper;
    
    public void logEvent(AuditEventType eventType, String userId, String sessionId, 
                        String resource, String action, String status) {
        logEvent(eventType, userId, sessionId, resource, action, status, null, null, null, null, null);
    }
    
    public void logEvent(AuditEventType eventType, String userId, String sessionId, 
                        String resource, String action, String status, String details) {
        logEvent(eventType, userId, sessionId, resource, action, status, details, null, null, null, null);
    }
    
    public void logEvent(AuditEventType eventType, String userId, String sessionId, 
                        String resource, String action, String status, String details,
                        String sourceIp, String userAgent, Map<String, Object> metadata, Long duration) {
        
        AuditEvent event = AuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType.getEventType())
                .userId(userId)
                .sessionId(sessionId)
                .resource(resource)
                .action(action)
                .status(status)
                .details(details)
                .timestamp(Instant.now())
                .sourceIp(sourceIp)
                .userAgent(userAgent)
                .metadata(metadata)
                .duration(duration)
                .build();
        
        logAuditEvent(event);
    }
    
    public void logFailureEvent(AuditEventType eventType, String userId, String sessionId, 
                               String resource, String action, String errorMessage) {
        AuditEvent event = AuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType.getEventType())
                .userId(userId)
                .sessionId(sessionId)
                .resource(resource)
                .action(action)
                .status("FAILURE")
                .errorMessage(errorMessage)
                .timestamp(Instant.now())
                .build();
        
        logAuditEvent(event);
    }
    
    private void logAuditEvent(AuditEvent event) {
        try {
            String jsonEvent = objectMapper.writeValueAsString(event);
            log.info("AUDIT_EVENT: {}", jsonEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit event: {}", event, e);
        }
    }
}