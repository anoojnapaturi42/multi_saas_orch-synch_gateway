package com.example.gateway.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {}
