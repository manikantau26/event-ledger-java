package com.eventledger.gateway;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<EventEntity, String> {
    List<EventEntity> findByAccountIdOrderByEventTimestampAscCreatedAtAsc(String accountId);
}