package com.wealthpilot.quote.store;

import java.util.Optional;

import javax.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface JPAQuoteIdentifierRepository extends JpaRepository<JPAQuoteIdentifier, Long> {
    Optional<JPAQuoteIdentifier> findOneByIsin(String isin);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<JPAQuoteIdentifier> findAndLockOneByIsin(String isin);
}
