package com.eventledger.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {
    List<TransactionEntity> findByAccountIdOrderByEventTimestampDesc(String accountId);
}