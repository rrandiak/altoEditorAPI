package cz.inovatika.altoEditor.presentation.dto.request;

import lombok.Data;

/** Filter request for user search (used with Spring Pageable). */
@Data
public class UserSearchRequest {

    private Boolean isKramerius;
    private Boolean isEngine;
    private Boolean isEnabled;
}
