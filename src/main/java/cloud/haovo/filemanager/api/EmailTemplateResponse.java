package cloud.haovo.filemanager.api;

import lombok.Value;

import java.util.List;

@Value
public class EmailTemplateResponse {
    String key;
    String name;
    String subject;
    String htmlBody;
    String textBody;
    List<String> variables;
}
