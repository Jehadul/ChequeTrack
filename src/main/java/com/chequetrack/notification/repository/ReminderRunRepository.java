package com.chequetrack.notification.repository;

import com.chequetrack.notification.domain.ReminderRun;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReminderRunRepository extends JpaRepository<ReminderRun, Long> {

    List<ReminderRun> findByCompanyIdOrderByStartedAtDesc(Long companyId, Limit limit);
}
