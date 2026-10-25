package com.chequetrack.issued.repository;

import com.chequetrack.issued.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

/** Branch-scoped: JPQL queries are limited by the branch filter automatically. */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
