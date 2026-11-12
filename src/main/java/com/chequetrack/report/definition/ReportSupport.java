package com.chequetrack.report.definition;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.web.DisplayFormat;
import com.chequetrack.lifecycle.domain.LifecycleStatus;
import com.chequetrack.organization.domain.Branch;
import com.chequetrack.organization.repository.BranchRepository;
import com.chequetrack.report.model.ReportFilter;
import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.repository.BankRepository;
import com.chequetrack.setup.repository.PartyRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ReportSupport {

    private final BranchRepository branchRepository;
    private final BankRepository bankRepository;
    private final PartyRepository partyRepository;
    private final DisplayFormat format;

    public ReportSupport(BranchRepository branchRepository, BankRepository bankRepository,
                         PartyRepository partyRepository, DisplayFormat format) {
        this.branchRepository = branchRepository;
        this.bankRepository = bankRepository;
        this.partyRepository = partyRepository;
        this.format = format;
    }

    /** Branch codes by id, for the Branch column. */
    public Map<Long, String> branchCodes() {
        return branchRepository.findByCompanyIdOrderByCode(CurrentUser.get().getCompanyId()).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getCode));
    }

    /** A branch chosen in the filter must be one the user may see. */
    public void checkBranch(Long branchId) {
        if (branchId != null && !CurrentUser.get().canAccessBranch(branchId)) {
            throw new BusinessException("Choose one of your branches.");
        }
    }

    /** Branches whose rows the user may see, or null for all of the company (tables that are not branch-scoped). */
    public Collection<Long> visibleBranches(Long chosen) {
        AppUserPrincipal me = CurrentUser.get();
        if (chosen != null) {
            return Set.of(chosen);
        }
        return me.isAllBranches() ? null : (me.getBranchIds().isEmpty() ? Set.of(-1L) : me.getBranchIds());
    }

    /** "Cheque date 01/11/2026 – 30/11/2026 · Branch DHK · Status In hand" for the report heading. */
    public String describe(ReportFilter f, ReportDefinition def) {
        Long companyId = CurrentUser.get().getCompanyId();
        List<String> parts = new ArrayList<>();
        if (f.getFrom() != null || f.getTo() != null) {
            parts.add(def.dateLabel() + " " + (f.getFrom() == null ? "…" : format.date(f.getFrom())) + " – "
                    + (f.getTo() == null ? "…" : format.date(f.getTo())));
        }
        if (f.getBranchId() != null) {
            branchRepository.findByIdAndCompanyId(f.getBranchId(), companyId).ifPresent(b -> parts.add("Branch " + b.getCode()));
        }
        if (f.getBankId() != null) {
            bankRepository.findByIdAndCompanyId(f.getBankId(), companyId).ifPresent(b -> parts.add("Bank " + b.getName()));
        }
        if (f.getStatus() != null && !f.getStatus().isBlank()) {
            def.statuses().stream().filter(s -> s[0].equals(f.getStatus())).findFirst().ifPresent(s -> parts.add("Status " + s[1]));
        }
        if (f.getPartyId() != null) {
            partyRepository.findByIdAndCompanyId(f.getPartyId(), companyId).ifPresent(p -> parts.add(p.getName()));
        }
        return parts.isEmpty() ? "All your branches, no filters" : String.join(" · ", parts);
    }

    public String date(java.time.LocalDate d) {
        return format.date(d);
    }

    public String amount(java.math.BigDecimal v) {
        return format.amount(v);
    }

    public String timestamp(java.time.Instant i) {
        return format.ts(i);
    }

    public static <S extends Enum<S> & LifecycleStatus> List<String[]> statusOptions(Class<S> type) {
        List<String[]> list = new ArrayList<>();
        for (S s : type.getEnumConstants()) {
            list.add(new String[] {s.name(), s.getLabel()});
        }
        return list;
    }

    public static <S extends Enum<S>> S status(String value, Class<S> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Choose a valid status.");
        }
    }
}
