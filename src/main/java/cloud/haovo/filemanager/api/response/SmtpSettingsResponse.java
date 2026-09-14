package cloud.haovo.filemanager.api.response;

import lombok.Value;

@Value
public class SmtpSettingsResponse {
    boolean enabled;
    String host;
    int port;
    String username;
    boolean passwordConfigured;
    boolean startTls;
    boolean auth;
    String fromEmail;
    String fromName;
    String otpSubject;
}


