package com.example.aidocumentassistant.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    @Query("""
            select chunk
            from DocumentChunk chunk
            join fetch chunk.document document
            where document.owner.id = :ownerId
              and document.status = com.example.aidocumentassistant.document.DocumentStatus.INDEXED
            """)
    List<DocumentChunk> findIndexedChunksForOwner(@Param("ownerId") Long ownerId);

    void deleteAllByDocument(Document document);
}
