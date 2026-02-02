package cz.inovatika.altoEditor.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;

import cz.inovatika.altoEditor.domain.enums.IndexingOperation;
import cz.inovatika.altoEditor.domain.enums.IndexingStatus;

@Entity
@Table(name = "indexing_journal")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexingJournalEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID uuid;

    @Column(nullable = false)
    private IndexingOperation operation;

    @Column(nullable = false)
    private IndexingStatus status;

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private String errorMessage;
}
