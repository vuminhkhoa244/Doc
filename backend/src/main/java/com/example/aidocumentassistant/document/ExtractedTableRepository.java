package com.example.aidocumentassistant.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExtractedTableRepository extends JpaRepository<ExtractedTable, Long> {

    List<ExtractedTable> findByDocumentIdOrderByPageAsc(Long documentId);

    @Modifying
    @Query("delete from ExtractedTable t where t.document = :document")
    void deleteAllByDocument(Document document);
}
