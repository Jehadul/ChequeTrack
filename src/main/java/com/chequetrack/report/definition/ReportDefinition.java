package com.chequetrack.report.definition;

import com.chequetrack.report.model.ReportFilter;
import com.chequetrack.report.model.ReportTable;
import com.chequetrack.setup.domain.PartyType;

import java.util.List;
import java.util.Set;

public interface ReportDefinition {

    int MAX_ROWS = 5000;

    /** URL code, e.g. "received". */
    String code();

    String title();

    String description();

    /** The user needs any one of these permissions. */
    Set<String> permissions();

    Set<ReportFilter.Field> filters();

    /** What the date filter applies to, e.g. "Cheque date". */
    default String dateLabel() {
        return "Date";
    }

    /** Status choices: [value, label]. */
    default List<String[]> statuses() {
        return List.of();
    }

    /** Customers, suppliers or neither for the party filter. */
    default PartyType partyType() {
        return null;
    }

    ReportTable run(ReportFilter filter, String filterText);
}
