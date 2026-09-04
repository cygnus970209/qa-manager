package com.qamanager.search;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SearchDocumentRepository extends JpaRepository<SearchDocument, Long> {

    Optional<SearchDocument> findByEntityTypeAndEntityId(String entityType, Long entityId);

    List<SearchDocument> findAllByEntityType(String entityType);

    List<SearchDocument> findAllByEntityTypeAndQaItemId(String entityType, Long qaItemId);

    void deleteByEntityTypeAndEntityId(String entityType, Long entityId);

    void deleteByProjectId(Long projectId);

    void deleteByUpdateId(Long updateId);

    void deleteByQaItemId(Long qaItemId);

    @Modifying
    @Query("delete from SearchDocument d")
    void deleteAllDocuments();

    @Query("select d.entityType, count(d) from SearchDocument d group by d.entityType")
    List<Object[]> countByType();
}
