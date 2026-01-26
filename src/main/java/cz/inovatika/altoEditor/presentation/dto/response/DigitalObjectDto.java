package cz.inovatika.altoEditor.presentation.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigitalObjectDto {
    
    private Integer id;
    private String pid;
    private String label;
    private String parentPath;
    private String parentLabel;
    private Integer version;
    private LocalDateTime date;
    private String state;

    private String altoContent;
}
