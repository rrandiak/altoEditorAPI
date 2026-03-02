package cz.inovatika.altoEditor.presentation.dto.response;

import lombok.Data;

@Data
public class KrameriusInstance {
    
    private String code;
    private String title;
    private String loginUrl;
    private String logoutUrl;
    private String exchangeCodeForTokenUrl;
    private String clientUrl;
    private String adminUrl;
    private String defaultLanguage;
}
