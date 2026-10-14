package com.chequetrack.setup.repository;

import com.chequetrack.setup.domain.Party;
import com.chequetrack.setup.domain.PartyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {

    /** Search by code or name; {@code types} limits customers/suppliers. */
    @Query("select p from Party p where p.companyId = :companyId and p.partyType in :types "
            + "and (:q = '' or lower(p.name) like :q or lower(p.code) like :q)")
    Page<Party> search(@Param("companyId") Long companyId, @Param("types") Collection<PartyType> types,
                       @Param("q") String likeOrEmpty, Pageable pageable);

    @Query("select p from Party p where p.companyId = :companyId and p.active = true and p.partyType in :types "
            + "order by p.name")
    List<Party> findSelectable(@Param("companyId") Long companyId, @Param("types") Collection<PartyType> types);

    Optional<Party> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndCodeIgnoreCase(Long companyId, String code);
}
