package com.example.gateway.repository;

import com.example.gateway.domain.StepExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StepExecutionLogRepository extends JpaRepository<StepExecutionLog, UUID> {}
