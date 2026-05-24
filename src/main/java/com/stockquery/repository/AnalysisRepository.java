package com.stockquery.repository;

import com.stockquery.model.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    List<Analysis> findByClientIpOrderByCreatedAtDesc(String clientIp);

    List<Analysis> findByStockCodeAndClientIpOrderByCreatedAtDesc(String stockCode, String clientIp);

    Optional<Analysis> findTopByStockCodeAndClientIpAndCreatedAtBetweenOrderByCreatedAtDesc(
            String stockCode, String clientIp, OffsetDateTime start, OffsetDateTime end);
}
