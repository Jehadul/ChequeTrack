package com.chequetrack.common.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Gives out document numbers such as {@code RCPT-2026-00001}, per company, type and year.
 * One atomic {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING} statement takes the
 * next number, so two users saving at the same time can never get the same one
 * (the row stays locked until the calling transaction ends).
 */
@Service
public class DocumentNumberService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DocumentNumberService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Next number of the given type, e.g. next(companyId, "RCPT") -> "RCPT-2026-00001". */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Long companyId, String docType) {
        int year = LocalDate.now(clock).getYear();
        Long value = jdbc.queryForObject("""
                INSERT INTO doc_sequence (company_id, doc_type, year, last_value) VALUES (?, ?, ?, 1)
                ON CONFLICT (company_id, doc_type, year) DO UPDATE SET last_value = doc_sequence.last_value + 1
                RETURNING last_value
                """, Long.class, companyId, docType, year);
        return String.format("%s-%d-%05d", docType, year, value);
    }
}
