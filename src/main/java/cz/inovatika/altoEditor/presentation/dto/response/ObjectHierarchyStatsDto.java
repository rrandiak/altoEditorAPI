package cz.inovatika.altoEditor.presentation.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// TODO: either use, or implement indexer and remove thos
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectHierarchyStatsDto {

    private int totalPages;
    private int pagesWithAlto;
    private List<VersionsPerUser> versionsPerUser;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionsPerUser {
        private Integer userId;
        private String username;
        private int versionsCount;
    } 
}
