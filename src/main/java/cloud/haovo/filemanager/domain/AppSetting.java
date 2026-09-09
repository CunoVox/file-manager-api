package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
public class AppSetting {
    @Id
    @Column(name = "setting_key", length = 120)
    private String key;

    @Lob
    @Column(name = "setting_value", nullable = false, columnDefinition = "LONGTEXT")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
