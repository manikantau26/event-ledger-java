package com.eventledger.gateway;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for event persistence and retrieval operations.
 */
public interface EventRepository extends JpaRepository<EventEntity, String> {
    List<EventEntity> findByAccountIdOrderByEventTimestampAscCreatedAtAsc(String accountId);
}