package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(String userId);

    @Modifying
    @Transactional
    @Query("update Notification notification set notification.readAt = :readAt where notification.userId = :userId and notification.readAt is null")
    int markAllRead(@Param("userId") String userId, @Param("readAt") Instant readAt);
}
