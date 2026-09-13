package com.example.gateway.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String userId, String action, String resourceTarget, String ipAddress,
                      Map<String, Object> oldState, Map<String, Object> newState) {
        repository.save(new AuditLogEntity(userId, action, resourceTarget, ipAddress, oldState, newState));
    }
}
