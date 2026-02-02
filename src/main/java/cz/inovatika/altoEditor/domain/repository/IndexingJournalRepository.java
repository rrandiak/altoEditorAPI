package cz.inovatika.altoEditor.domain.repository;

    
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cz.inovatika.altoEditor.domain.enums.IndexingStatus;
import cz.inovatika.altoEditor.domain.model.IndexingJournalEntry;

@Repository
public interface IndexingJournalRepository extends JpaRepository<IndexingJournalEntry, Integer> {

    List<IndexingJournalEntry> findByStatus(IndexingStatus status);
}

