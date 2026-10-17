package com.chequetrack.attachment.repository;

import com.chequetrack.attachment.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByCompanyIdAndOwnerTypeAndOwnerIdOrderByCreatedAtDesc(Long companyId, String ownerType, Long ownerId);

    long countByCompanyIdAndOwnerTypeAndOwnerId(Long companyId, String ownerType, Long ownerId);

    Optional<Attachment> findByIdAndCompanyId(Long id, Long companyId);
}
