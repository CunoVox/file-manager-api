package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findTop10ByOrderByCreatedAtDesc();

    @Query("select log from AuditLog log where " +
            "(:action is null or log.action = :action) and " +
            "(:from is null or log.createdAt >= :from) and " +
            "(:to is null or log.createdAt <= :to) and " +
            "(:keyword is null or lower(log.actorEmail) like lower(concat('%', :keyword, '%')) " +
            "or lower(log.targetName) like lower(concat('%', :keyword, '%')) " +
            "or lower(log.message) like lower(concat('%', :keyword, '%'))) " +
            "order by log.createdAt desc")
    Page<AuditLog> search(@Param("action") String action, @Param("keyword") String keyword,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
