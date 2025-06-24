package com.coherentsolutions.l09clientmcp.production.audit;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class AuditEvent {
    private String eventId;
    private String eventType;
    private String userId;
    private String sessionId;
    private String resource;
    private String action;
    private String status;
    private String details;
    private Instant timestamp;
    private String sourceIp;
    private String userAgent;
    private Map<String, Object> metadata;
    private Long duration;
    private String errorMessage;
}