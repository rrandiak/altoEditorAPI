package cz.inovatika.altoEditor.presentation.dto.response;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Single ALTO version response; {@code content} is ALTO XML (Base64 in JSON). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AltoVersionDto {
    
    private Integer id;

    private String pid;
    private Integer version;
    private String username;

    private AltoVersionState state;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String pageTitle;
    private List<String> ancestorTitles;

    private byte[] content;
}
