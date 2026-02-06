package cz.inovatika.altoEditor.presentation.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Generic paginated search result (offset/limit style). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultsDto<T> {

    List<T> items;
    long total;
}
