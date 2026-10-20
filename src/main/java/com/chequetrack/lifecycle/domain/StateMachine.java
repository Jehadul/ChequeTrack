package com.chequetrack.lifecycle.domain;

import com.chequetrack.setup.domain.ApprovalAction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The allowed status changes of one kind of record, built from a status enum.
 * Statuses are handled by name so the engine can serve any record type.
 */
public final class StateMachine<S extends Enum<S> & LifecycleStatus> {

    private final String name;
    private final Class<S> statusType;
    private final S initial;
    private final List<Transition> transitions;

    private StateMachine(String name, Class<S> statusType, S initial, List<Transition> transitions) {
        this.name = name;
        this.statusType = statusType;
        this.initial = initial;
        this.transitions = List.copyOf(transitions);
    }

    public static <S extends Enum<S> & LifecycleStatus> Builder<S> builder(String name, Class<S> statusType, S initial) {
        return new Builder<>(name, statusType, initial);
    }

    public String getName() {
        return name;
    }

    public S getInitial() {
        return initial;
    }

    public List<S> getStatuses() {
        return Arrays.asList(statusType.getEnumConstants());
    }

    public List<Transition> getTransitions() {
        return transitions;
    }

    public S status(String name) {
        return Enum.valueOf(statusType, name);
    }

    public Optional<Transition> find(String action) {
        return transitions.stream().filter(t -> t.action().equals(action)).findFirst();
    }

    /** Actions possible in this status, for showing buttons. */
    public List<Transition> availableFrom(String status) {
        return transitions.stream().filter(t -> t.allowedFrom(status)).toList();
    }

    /** "In hand, Bounced" */
    public String fromLabels(Transition t) {
        return t.from().stream().map(s -> status(s).getLabel()).collect(Collectors.joining(", "));
    }

    public String label(String status) {
        return status(status).getLabel();
    }

    public static final class Builder<S extends Enum<S> & LifecycleStatus> {
        private final String name;
        private final Class<S> statusType;
        private final S initial;
        private final List<Transition> transitions = new ArrayList<>();

        private Builder(String name, Class<S> statusType, S initial) {
            this.name = name;
            this.statusType = statusType;
            this.initial = initial;
        }

        /** A change that never needs approval. */
        public Builder<S> on(String action, String label, Set<S> from, S to, boolean reasonRequired) {
            return add(action, label, from, to, null, null, null, reasonRequired);
        }

        /** A change that needs approval when the rules for {@code approval} require it. */
        public Builder<S> onApproved(String action, String label, Set<S> from, S to, ApprovalAction approval,
                                     boolean reasonRequired) {
            return add(action, label, from, to, approval, null, null, reasonRequired);
        }

        /** Like {@link #onApproved}, but the record shows {@code pending} while waiting and {@code rejected} if refused. */
        public Builder<S> onApprovedVia(String action, String label, Set<S> from, S to, ApprovalAction approval,
                                        S pending, S rejected) {
            return add(action, label, from, to, approval, pending, rejected, false);
        }

        private Builder<S> add(String action, String label, Set<S> from, S to, ApprovalAction approval,
                               S pending, S rejected, boolean reasonRequired) {
            Set<String> fromNames = from.stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));
            transitions.add(new Transition(action, label, Collections.unmodifiableSet(fromNames), to.name(), approval,
                    pending == null ? null : pending.name(), rejected == null ? null : rejected.name(), reasonRequired));
            return this;
        }

        public StateMachine<S> build() {
            Set<String> actions = new LinkedHashSet<>();
            for (Transition t : transitions) {
                if (!actions.add(t.action())) {
                    throw new IllegalStateException("Duplicate action " + t.action() + " in " + name);
                }
            }
            return new StateMachine<>(name, statusType, initial, transitions);
        }
    }
}
