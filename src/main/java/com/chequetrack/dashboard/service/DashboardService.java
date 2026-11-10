package com.chequetrack.dashboard.service;

import com.chequetrack.audit.domain.StatusHistoryEntry;
import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.lifecycle.domain.IssuedStatus;
import com.chequetrack.lifecycle.domain.ReceivedStatus;
import com.chequetrack.received.domain.ChequeType;
import com.chequetrack.received.domain.ReceivedCheque;
import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    public static final int FORECAST_WEEKS = 12;
    public static final int ANALYTICS_MONTHS = 12;

    private static final Set<ReceivedStatus> EXPECTED_IN = EnumSet.of(ReceivedStatus.IN_HAND, ReceivedStatus.DEPOSITED);
    private static final Set<IssuedStatus> EXPECTED_OUT = EnumSet.of(IssuedStatus.APPROVED, IssuedStatus.PRINTED, IssuedStatus.ISSUED);
    private static final Set<IssuedStatus> WRITTEN = EnumSet.of(IssuedStatus.PRINTED, IssuedStatus.ISSUED, IssuedStatus.CLEARED);
    private static final DateTimeFormatter WEEK = DateTimeFormatter.ofPattern("dd MMM");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy");

    private final EntityManager em;
    private final Clock clock;

    public DashboardService(EntityManager em, Clock clock) {
        this.em = em;
        this.clock = clock;
    }

    public record Figure(long count, BigDecimal amount) {
    }

    public record Kpis(Figure inHand, Figure dueToDeposit, Figure dueToPay, long bouncedThisMonth, BigDecimal bouncedAmount,
                       LocalDate weekEnd) {
    }

    /** Expected money in and out per week; week 0 also holds what is already overdue. */
    public record Forecast(List<String> labels, List<BigDecimal> moneyIn, List<BigDecimal> moneyOut, BigDecimal totalIn,
                           BigDecimal totalOut) {
    }

    public record Activity(java.time.Instant at, String username, String kind, String number, String from, String to,
                           String reason, String link) {
    }

    public record Ranked(String name, long count, BigDecimal amount) {
    }

    public record BounceRate(String name, long cheques, long bounced) {
        public int percent() {
            return cheques == 0 ? 0 : (int) Math.round(100.0 * bounced / cheques);
        }
    }

    public record Analytics(LocalDate from, List<String> months, List<BigDecimal> received, List<BigDecimal> issued,
                            List<Ranked> topCustomers, List<Ranked> topSuppliers, List<BounceRate> bounceByCustomer,
                            List<BounceRate> bounceByBank, Figure receivedTotal, Figure issuedTotal, long bouncedCheques) {
    }

    // ---- home page -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Kpis kpis() {
        LocalDate today = LocalDate.now(clock);
        LocalDate weekEnd = today.plusDays(6);
        Figure inHand = figure("select count(c), coalesce(sum(c.amount), 0) from ReceivedCheque c where c.status = :st",
                Map.of("st", ReceivedStatus.IN_HAND));
        Figure deposit = figure("select count(c), coalesce(sum(c.amount), 0) from ReceivedCheque c where c.status = :st "
                        + "and c.chequeType <> :security and c.chequeDate <= :end",
                Map.of("st", ReceivedStatus.IN_HAND, "security", ChequeType.SECURITY, "end", weekEnd));
        Figure pay = figure("select count(c), coalesce(sum(c.amount), 0) from IssuedCheque c where c.status = :st "
                + "and c.chequeDate <= :end", Map.of("st", IssuedStatus.ISSUED, "end", weekEnd));
        Figure bounced = figure("select count(c), coalesce(sum(c.amount), 0) from ReceivedCheque c "
                + "where c.lastBounceDate >= :from", Map.of("from", today.withDayOfMonth(1)));
        return new Kpis(inHand, deposit, pay, bounced.count(), bounced.amount(), weekEnd);
    }

    @Transactional(readOnly = true)
    public Forecast forecast() {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = today.plusWeeks(FORECAST_WEEKS).minusDays(1);
        BigDecimal[] in = zeros(FORECAST_WEEKS);
        BigDecimal[] out = zeros(FORECAST_WEEKS);
        for (Object[] r : em.createQuery("select c.chequeDate, c.amount from ReceivedCheque c where c.status in :st "
                        + "and c.chequeType <> :security and c.chequeDate <= :end", Object[].class)
                .setParameter("st", EXPECTED_IN).setParameter("security", ChequeType.SECURITY).setParameter("end", end)
                .getResultList()) {
            add(in, week(today, (LocalDate) r[0]), (BigDecimal) r[1]);
        }
        for (Object[] r : em.createQuery("select c.chequeDate, c.amount from IssuedCheque c where c.status in :st "
                        + "and c.chequeDate <= :end", Object[].class)
                .setParameter("st", EXPECTED_OUT).setParameter("end", end)
                .getResultList()) {
            add(out, week(today, (LocalDate) r[0]), (BigDecimal) r[1]);
        }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < FORECAST_WEEKS; i++) {
            labels.add(i == 0 ? "This week" : WEEK.format(today.plusWeeks(i)));
        }
        return new Forecast(labels, List.of(in), List.of(out), sum(in), sum(out));
    }

    /** Latest status changes of cheques in the user's branches. */
    @Transactional(readOnly = true)
    public List<Activity> recentActivity(int max) {
        AppUserPrincipal me = CurrentUser.get();
        String jpql = "select h from StatusHistoryEntry h where h.companyId = :co and h.entityType in :types"
                + (me.isAllBranches() ? "" : " and h.branchId in :branches") + " order by h.changedAt desc, h.id desc";
        TypedQuery<StatusHistoryEntry> q = em.createQuery(jpql, StatusHistoryEntry.class)
                .setParameter("co", me.getCompanyId())
                .setParameter("types", List.of(ReceivedCheque.RECORD_TYPE, IssuedCheque.RECORD_TYPE))
                .setMaxResults(max);
        if (!me.isAllBranches()) {
            q.setParameter("branches", me.getBranchIds().isEmpty() ? Set.of(-1L) : me.getBranchIds());
        }
        List<StatusHistoryEntry> rows = q.getResultList();
        Map<Long, String> received = numbers(ReceivedCheque.class, ids(rows, ReceivedCheque.RECORD_TYPE));
        Map<Long, String> issued = numbers(IssuedCheque.class, ids(rows, IssuedCheque.RECORD_TYPE));
        List<Activity> list = new ArrayList<>();
        for (StatusHistoryEntry h : rows) {
            boolean isReceived = ReceivedCheque.RECORD_TYPE.equals(h.getEntityType());
            String number = (isReceived ? received : issued).get(h.getEntityId());
            if (number == null) {
                continue;  // a cheque the user may no longer see
            }
            list.add(new Activity(h.getChangedAt(), h.getUsername(), isReceived ? "Received" : "Issued", number,
                    label(isReceived, h.getFromStatus()), label(isReceived, h.getToStatus()), h.getReason(),
                    (isReceived ? "/received/" : "/issued/") + h.getEntityId()));
        }
        return list;
    }

    // ---- analytics -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Analytics analytics() {
        LocalDate today = LocalDate.now(clock);
        YearMonth first = YearMonth.from(today).minusMonths(ANALYTICS_MONTHS - 1L);
        LocalDate from = first.atDay(1);

        Map<YearMonth, BigDecimal> rec = monthly("select year(c.receivedDate), month(c.receivedDate), sum(c.amount) "
                + "from ReceivedCheque c where c.receivedDate >= :from and c.status <> :cancelled "
                + "group by year(c.receivedDate), month(c.receivedDate)", Map.of("from", from, "cancelled", ReceivedStatus.CANCELLED));
        Map<YearMonth, BigDecimal> iss = monthly("select year(c.chequeDate), month(c.chequeDate), sum(c.amount) "
                + "from IssuedCheque c where c.chequeDate >= :from and c.status in :st "
                + "group by year(c.chequeDate), month(c.chequeDate)", Map.of("from", from, "st", WRITTEN));
        List<String> months = new ArrayList<>();
        List<BigDecimal> received = new ArrayList<>();
        List<BigDecimal> issued = new ArrayList<>();
        for (int i = 0; i < ANALYTICS_MONTHS; i++) {
            YearMonth m = first.plusMonths(i);
            months.add(MONTH.format(m));
            received.add(rec.getOrDefault(m, BigDecimal.ZERO));
            issued.add(iss.getOrDefault(m, BigDecimal.ZERO));
        }

        List<Ranked> customers = ranked("select p.name, count(c), sum(c.amount) from ReceivedCheque c join c.party p "
                + "where c.receivedDate >= :from and c.status <> :cancelled group by p.id, p.name order by sum(c.amount) desc",
                Map.of("from", from, "cancelled", ReceivedStatus.CANCELLED));
        List<Ranked> suppliers = ranked("select p.name, count(c), sum(c.amount) from IssuedCheque c join c.party p "
                + "where c.chequeDate >= :from and c.status in :st group by p.id, p.name order by sum(c.amount) desc",
                Map.of("from", from, "st", WRITTEN));
        List<BounceRate> byCustomer = bounceRates("select p.name, count(c), sum(case when c.bounceCount > 0 then 1 else 0 end) "
                + "from ReceivedCheque c join c.party p where c.receivedDate >= :from and c.status <> :cancelled "
                + "group by p.id, p.name", from);
        List<BounceRate> byBank = bounceRates("select b.name, count(c), sum(case when c.bounceCount > 0 then 1 else 0 end) "
                + "from ReceivedCheque c join c.drawerBank b where c.receivedDate >= :from and c.status <> :cancelled "
                + "group by b.id, b.name", from);

        Figure recTotal = figure("select count(c), coalesce(sum(c.amount), 0) from ReceivedCheque c "
                + "where c.receivedDate >= :from and c.status <> :cancelled", Map.of("from", from, "cancelled", ReceivedStatus.CANCELLED));
        Figure issTotal = figure("select count(c), coalesce(sum(c.amount), 0) from IssuedCheque c "
                + "where c.chequeDate >= :from and c.status in :st", Map.of("from", from, "st", WRITTEN));
        long bounced = byCustomer.stream().mapToLong(BounceRate::bounced).sum();
        return new Analytics(from, months, received, issued, top(customers, 10), top(suppliers, 10),
                byCustomer.stream().filter(b -> b.bounced() > 0).limit(10).toList(),
                byBank, recTotal, issTotal, bounced);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private Figure figure(String jpql, Map<String, Object> params) {
        TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class);
        params.forEach(q::setParameter);
        Object[] r = q.getSingleResult();
        return new Figure(((Number) r[0]).longValue(), (BigDecimal) r[1]);
    }

    private Map<YearMonth, BigDecimal> monthly(String jpql, Map<String, Object> params) {
        TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class);
        params.forEach(q::setParameter);
        Map<YearMonth, BigDecimal> m = new HashMap<>();
        for (Object[] r : q.getResultList()) {
            m.put(YearMonth.of(((Number) r[0]).intValue(), ((Number) r[1]).intValue()), (BigDecimal) r[2]);
        }
        return m;
    }

    private List<Ranked> ranked(String jpql, Map<String, Object> params) {
        TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class);
        params.forEach(q::setParameter);
        return q.getResultList().stream()
                .map(r -> new Ranked((String) r[0], ((Number) r[1]).longValue(), (BigDecimal) r[2]))
                .toList();
    }

    /** Highest bounce share first; ties by number of cheques. */
    private List<BounceRate> bounceRates(String jpql, LocalDate from) {
        return em.createQuery(jpql, Object[].class)
                .setParameter("from", from).setParameter("cancelled", ReceivedStatus.CANCELLED)
                .getResultList().stream()
                .map(r -> new BounceRate((String) r[0], ((Number) r[1]).longValue(), ((Number) r[2]).longValue()))
                .sorted((a, b) -> a.percent() != b.percent() ? Integer.compare(b.percent(), a.percent())
                        : Long.compare(b.cheques(), a.cheques()))
                .toList();
    }

    private static <T> List<T> top(List<T> list, int n) {
        return list.size() <= n ? list : list.subList(0, n);
    }

    private <T> Map<Long, String> numbers(Class<T> type, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String entity = type.getSimpleName();
        return em.createQuery("select c.id, c.chequeNumber from " + entity + " c where c.id in :ids", Object[].class)
                .setParameter("ids", ids).getResultList().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (String) r[1], (a, b) -> a, LinkedHashMap::new));
    }

    private static Set<Long> ids(List<StatusHistoryEntry> rows, String type) {
        return rows.stream().filter(h -> type.equals(h.getEntityType())).map(StatusHistoryEntry::getEntityId)
                .collect(Collectors.toSet());
    }

    private static String label(boolean received, String status) {
        if (status == null) {
            return null;
        }
        Function<String, String> f = received ? s -> ReceivedStatus.valueOf(s).getLabel() : s -> IssuedStatus.valueOf(s).getLabel();
        return f.apply(status);
    }

    private static int week(LocalDate today, LocalDate date) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(today, date) / 7);
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        java.util.Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static void add(BigDecimal[] weeks, int i, BigDecimal amount) {
        if (i < weeks.length) {
            weeks[i] = weeks[i].add(amount);
        }
    }

    private static BigDecimal sum(BigDecimal[] a) {
        BigDecimal s = BigDecimal.ZERO;
        for (BigDecimal v : a) {
            s = s.add(v);
        }
        return s;
    }
}
