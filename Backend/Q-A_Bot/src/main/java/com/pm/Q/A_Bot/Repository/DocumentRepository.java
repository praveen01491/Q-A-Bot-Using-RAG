package com.pm.Q.A_Bot.Repository;

import com.pm.Q.A_Bot.Entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends
        JpaRepository<DocumentEntity, Long> {
}

