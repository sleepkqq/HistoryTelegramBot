package io.project.CJMTelegramBot.repository;

import io.project.CJMTelegramBot.model.HistoryDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HistoryDateRepository extends JpaRepository<HistoryDate, Long> {
}
