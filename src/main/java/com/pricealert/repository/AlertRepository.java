package com.pricealert.repository;
import com.pricealert.domain.alert.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;
public interface AlertRepository extends JpaRepository<Alert,Long> {
    boolean existsByFingerprint(String fingerprint);
    Optional<Alert> findFirstByProductIdOrderByCreatedAtDesc(Long productId);
    List<Alert> findTop10ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(String status, Instant now);
}

