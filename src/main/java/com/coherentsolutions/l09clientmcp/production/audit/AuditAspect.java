package com.coherentsolutions.l09clientmcp.production.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {
    
    private final AuditLogger auditLogger;
    
    @Around("@annotation(auditable)")
    public Object auditMethodExecution(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        // Extract request information
        String userId = getCurrentUserId();
        String sessionId = getCurrentSessionId();
        String sourceIp = getCurrentSourceIp();
        String userAgent = getCurrentUserAgent();
        
        // Create metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("method", methodName);
        metadata.put("class", className);
        metadata.put("arguments", joinPoint.getArgs());
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            auditLogger.logEvent(
                auditable.eventType(),
                userId,
                sessionId,
                auditable.resource(),
                auditable.action(),
                "SUCCESS",
                "Method executed successfully",
                sourceIp,
                userAgent,
                metadata,
                duration
            );
            
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            auditLogger.logFailureEvent(
                auditable.eventType(),
                userId,
                sessionId,
                auditable.resource(),
                auditable.action(),
                e.getMessage()
            );
            
            throw e;
        }
    }
    
    private String getCurrentUserId() {
        // In a real application, extract from SecurityContext
        return "system";
    }
    
    private String getCurrentSessionId() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        if (attr != null && attr.getRequest() != null) {
            return attr.getRequest().getSession().getId();
        }
        return "unknown";
    }
    
    private String getCurrentSourceIp() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        if (attr != null && attr.getRequest() != null) {
            HttpServletRequest request = attr.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return "unknown";
    }
    
    private String getCurrentUserAgent() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        if (attr != null && attr.getRequest() != null) {
            return attr.getRequest().getHeader("User-Agent");
        }
        return "unknown";
    }
}