package com.eventledger.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for account transaction persistence operations.
 */
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {
    List<TransactionEntity> findByAccountIdOrderByEventTimestampDesc(String accountId);
}