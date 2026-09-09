package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 30)
    private Set<UserRole> roles = EnumSet.of(UserRole.MEMBER);

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = false;

    @Column(name = "email_verified")
    private Boolean emailVerified = true;

    @Column(name = "storage_quota_bytes")
    private Long storageQuotaBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Set<UserRole> safeRoles() {
        return roles == null ? Collections.emptySet() : roles;
    }

    public boolean isEmailVerified() {
        return emailVerified == null || emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
}
