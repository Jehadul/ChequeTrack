package com.chequetrack.setup.repository;

import com.chequetrack.setup.domain.OpenItem;
import com.chequetrack.setup.domain.OpenItemStatus;
import com.chequetrack.setup.domain.OpenItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

/** Branch-scoped: queries are limited by the branch filter automatically. */
public interface OpenItemRepository extends JpaRepository<OpenItem, Long> {

    @Query(value = "select i from OpenItem i join fetch i.party p where i.itemType = :type and i.status in :statuses "
            + "and (:q = '' or lower(i.number) like :q or lower(p.name) like :q)",
            countQuery = "select count(i) from OpenItem i join i.party p where i.itemType = :type and i.status in :statuses "
                    + "and (:q = '' or lower(i.number) like :q or lower(p.name) like :q)")
    Page<OpenItem> search(@Param("type") OpenItemType type, @Param("statuses") Collection<OpenItemStatus> statuses,
                          @Param("q") String likeOrEmpty, Pageable pageable);

    /** Items of one party still to be settled, oldest first. */
    @Query("select i from OpenItem i where i.party.id = :partyId and i.itemType = :type "
            + "and i.status in (com.chequetrack.setup.domain.OpenItemStatus.OPEN, com.chequetrack.setup.domain.OpenItemStatus.PARTLY_SETTLED) "
            + "order by i.itemDate, i.id")
    java.util.List<OpenItem> findOpenForParty(@Param("partyId") Long partyId, @Param("type") OpenItemType type);

    /** Duplicate check across all branches (native SQL, so the branch filter does not hide other branches). */
    @Query(value = "select exists(select 1 from open_item where company_id = :companyId and item_type = :type "
            + "and upper(number) = upper(:number) and deleted = false and id <> coalesce(:excludeId, -1))",
            nativeQuery = true)
    boolean numberTaken(@Param("companyId") Long companyId, @Param("type") String type,
                        @Param("number") String number, @Param("excludeId") Long excludeId);
}
