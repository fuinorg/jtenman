package org.fuin.jtenman.command.core.domain.tenants;

import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ApplicationCatalogue}.
 */
public class ApplicationCatalogueTest {

    @Test
    public void testRolesDefaultToEmptyForAnApplicationThatNeedsNone() {
        final ApplicationCatalogue.Entry entry =
                new ApplicationCatalogue.Entry("app", "App", "app-api", "app-api");
        assertThat(entry.realmRoles()).isEmpty();
        assertThat(entry.realmManagementRoles()).isEmpty();
    }

    @Test
    public void testNullRoleListsBecomeEmptyRatherThanNull() {
        final ApplicationCatalogue.Entry entry =
                new ApplicationCatalogue.Entry("app", "App", "app-api", "app-api", null, null);
        assertThat(entry.realmRoles()).isEmpty();
        assertThat(entry.realmManagementRoles()).isEmpty();
    }

    @Test
    public void testRolesAreKept() {
        final ApplicationCatalogue.Entry entry = new ApplicationCatalogue.Entry("melkheftken", "Melkheftken",
                "melkheftken-api", "melkheftken-api",
                List.of("melkheftken-user", "melkheftken-admin"), List.of("manage-users", "view-users"));
        assertThat(entry.realmRoles()).containsExactly("melkheftken-user", "melkheftken-admin");
        assertThat(entry.realmManagementRoles()).containsExactly("manage-users", "view-users");
    }

    @Test
    public void testRoleListsAreDefensivelyCopied() {
        final List<String> mutable = new java.util.ArrayList<>(List.of("a"));
        final ApplicationCatalogue.Entry entry =
                new ApplicationCatalogue.Entry("app", "App", "app-api", "app-api", mutable, List.of());
        mutable.add("b");
        assertThat(entry.realmRoles()).containsExactly("a");
        assertThatThrownBy(() -> entry.realmRoles().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testLookup() {
        final ApplicationCatalogue testee = new ApplicationCatalogue(List.of(
                new ApplicationCatalogue.Entry("melkheftken", "Melkheftken", "melkheftken-api",
                        "melkheftken-api", List.of("melkheftken-user"), List.of("manage-users"))));

        assertThat(testee.contains(new ApplicationId("melkheftken"))).isTrue();
        assertThat(testee.contains(new ApplicationId("nope"))).isFalse();
        assertThat(testee.require(new ApplicationId("melkheftken")).realmRoles())
                .containsExactly("melkheftken-user");
        assertThatThrownBy(() -> testee.require(new ApplicationId("nope")))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
