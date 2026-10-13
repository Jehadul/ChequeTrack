package com.chequetrack.common.event;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChangesTest {

    @Test
    void recordsOnlyFieldsThatChanged() {
        Changes changes = new Changes()
                .add("name", "Head Office", "Head Office")
                .add("phone", null, "01700000000");

        assertThat(changes.asMap()).containsOnlyKeys("phone");
        @SuppressWarnings("unchecked")
        Map<String, Object> phone = (Map<String, Object>) changes.asMap().get("phone");
        assertThat(phone).containsEntry("from", null).containsEntry("to", "01700000000");
    }

    @Test
    void treatsBlankAndNullAsTheSame() {
        assertThat(new Changes().add("email", null, "  ").isEmpty()).isTrue();
        assertThat(new Changes().add("email", " a@b.com ", "a@b.com").isEmpty()).isTrue();
    }

    @Test
    void comparesCollectionsIgnoringOrder() {
        assertThat(new Changes().add("branches", List.of("HO", "CTG"), Set.of("CTG", "HO")).isEmpty()).isTrue();

        Changes changes = new Changes().add("permissions", Set.of("USER_VIEW", "BRANCH_VIEW"), Set.of("BRANCH_VIEW"));
        @SuppressWarnings("unchecked")
        Map<String, Object> change = (Map<String, Object>) changes.asMap().get("permissions");
        assertThat(change.get("from")).isEqualTo(List.of("BRANCH_VIEW", "USER_VIEW"));
        assertThat(change.get("to")).isEqualTo(List.of("BRANCH_VIEW"));
    }

    @Test
    void keepsFieldOrder() {
        Changes changes = new Changes().add("b", 1, 2).add("a", 1, 2).add("c", 1, 2);
        assertThat(changes.asMap().keySet()).containsExactly("b", "a", "c");
    }
}
