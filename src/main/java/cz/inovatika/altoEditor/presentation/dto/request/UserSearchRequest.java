package cz.inovatika.altoEditor.presentation.dto.request;

import lombok.Data;

@Data
public class UserSearchRequest {

    private Boolean isKramerius;
    private Boolean isEngine;
    private Boolean isEnabled;
}
