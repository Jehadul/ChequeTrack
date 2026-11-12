package com.chequetrack.report.definition;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Query {

    private final StringBuilder jpql;
    private final List<String> where = new ArrayList<>();
    private final Map<String, Object> params = new LinkedHashMap<>();
    private String orderBy = "";

    Query(String selectFrom) {
        this.jpql = new StringBuilder(selectFrom);
    }

    Query where(String condition) {
        where.add(condition);
        return this;
    }

    /** Adds {@code condition} (using {@code :name}) only when value is not null. */
    Query when(Object value, String condition, String name) {
        if (value != null && !(value instanceof String s && s.isBlank())) {
            where.add(condition);
            params.put(name, value);
        }
        return this;
    }

    Query param(String name, Object value) {
        params.put(name, value);
        return this;
    }

    Query orderBy(String orderBy) {
        this.orderBy = " order by " + orderBy;
        return this;
    }

    <T> List<T> list(EntityManager em, Class<T> type, int max) {
        String q = jpql + (where.isEmpty() ? "" : " where " + String.join(" and ", where)) + orderBy;
        TypedQuery<T> tq = em.createQuery(q, type).setMaxResults(max);
        params.forEach(tq::setParameter);
        return tq.getResultList();
    }
}
