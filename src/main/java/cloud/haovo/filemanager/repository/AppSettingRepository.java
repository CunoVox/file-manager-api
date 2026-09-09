package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
