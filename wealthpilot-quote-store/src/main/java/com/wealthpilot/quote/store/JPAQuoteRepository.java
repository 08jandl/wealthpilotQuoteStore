package com.wealthpilot.quote.store;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository("QuoteRepository") // set custom bean name to avoid conflict with com.wealthpilot.transactions.infrastructure.persistence.jpa.JPAQuoteRepository
public interface JPAQuoteRepository extends JpaRepository<JPAQuote, Long> {

    Optional<JPAQuote> findFirstByIdentifierOrderByQuoteDateDesc(JPAQuoteIdentifier identifier);

    Optional<JPAQuote> findByIdentifierAndQuoteDate(JPAQuoteIdentifier identifier, LocalDate date);

    List<JPAQuote> findAllByIdentifier(JPAQuoteIdentifier identifier);

    @Modifying
    @Query("delete from Quote where identifier = :identifier") // NOTE: JPAQuote has a custom entity-name "Quote" that must be used in queries!
    void deleteByIdentifier(JPAQuoteIdentifier identifier);
}
