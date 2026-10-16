package com.example.aidocumentassistant.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, Long> {

    List<ImageAsset> findByDocumentIdOrderByPageAsc(Long documentId);

    @Modifying
    @Query("delete from ImageAsset a where a.document = :document")
    void deleteAllByDocument(Document document);

    @Query("""
            select a from ImageAsset a
            join a.document d
            where a.id = :id and d.owner.id = :ownerId
            """)
    Optional<ImageAsset> findByIdForOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Query("""
            select a from ImageAsset a
            join a.document d
            where d.owner.id = :ownerId
              and (:q = '' or lower(coalesce(a.ocrText, '')) like lower(concat('%', :q, '%'))
                   or lower(d.filename) like lower(concat('%', :q, '%')))
            order by d.uploadTime desc, a.page asc
            """)
    List<ImageAsset> searchForOwner(@Param("ownerId") Long ownerId, @Param("q") String q);

    @Query("""
            select a from ImageAsset a
            join a.document d
            where d.owner.id = :ownerId and d.id = :documentId
              and (:q = '' or lower(coalesce(a.ocrText, '')) like lower(concat('%', :q, '%'))
                   or lower(d.filename) like lower(concat('%', :q, '%')))
            order by a.page asc
            """)
    List<ImageAsset> searchForOwnerInDocument(
            @Param("ownerId") Long ownerId,
            @Param("documentId") Long documentId,
            @Param("q") String q
    );
}
