package com.example.gateway.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Aspect
@Component
public class AuditLoggingAspect {
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final ExpressionParser parser = new SpelExpressionParser();

    public AuditLoggingAspect(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService; this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditLog)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), signature.getMethod(), joinPoint.getArgs(), new DefaultParameterNameDiscoverer());
        Object oldValue = evaluate(auditLog.oldState(), context);
        Object result;
        try { result = joinPoint.proceed(); }
        finally {
            Object newValue = evaluate(auditLog.newState(), context);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String userId = authentication == null ? "anonymous" : authentication.getName();
            auditLogService.write(userId, auditLog.action(), String.valueOf(evaluate(auditLog.resourceTarget(), context)),
                    ipAddress(), asMap(oldValue), asMap(newValue));
        }
        return result;
    }

    private Object evaluate(String expression, MethodBasedEvaluationContext context) {
        return expression == null || expression.isBlank() ? null : parser.parseExpression(expression).getValue(context);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value == null) return null;
        if (value instanceof JsonNode node && !node.isObject()) return Map.of("value", node);
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return objectMapper.convertValue(value, Map.class);
    }

    private String ipAddress() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) return null;
        HttpServletRequest request = servlet.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
