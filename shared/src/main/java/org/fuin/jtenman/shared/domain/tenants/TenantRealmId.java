/**
 * Copyright (C) 2026 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.jtenman.shared.domain.tenants;

import java.io.Serial;
import org.fuin.ddd4j.core.EntityType;
import org.fuin.ddd4j.core.HasEntityTypeConstant;
import org.fuin.objects4j.common.HasPublicStaticIsValidMethod;
import org.fuin.objects4j.common.HasPublicStaticValueOfMethod;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.annotation.concurrent.Immutable;
import org.jspecify.annotations.Nullable;

/**
 * Uniquely identifies a tenant by the name of its Keycloak realm.
 * <p>
 * The accepted values are the same as those of {@link RealmName}, and they are not a style choice: the
 * resource-server side derives an {@code org.fuin.ddd4j.core.TenantId} from the realm segment of the
 * token issuer while decoding, and that type accepts 2-10 characters matching
 * {@value #PATTERN_STRING}. Refusing anything else here keeps the failure in the control plane, where a
 * person sees it, instead of in every consuming application, where it would surface as a 500 while
 * decoding a token.
 */
@Immutable
@HasEntityTypeConstant
@HasPublicStaticIsValidMethod
@HasPublicStaticValueOfMethod
public final class TenantRealmId extends AbstractTenantRealmId {

    @Serial
    private static final long serialVersionUID = 1000L;

    /**
     * Name that identifies the entity uniquely within the context.
     * <p>
     * Declared here as well as on the parent because {@code JandexEntityIdFactory} reads the constant from
     * the annotated class. Without it the factory knows no identifier at all, and every command is
     * rejected with "Unknown type: Tenant (Known types are: [])" - a failure a long way from its cause.
     */
    public static final EntityType TYPE = AbstractTenantRealmId.TYPE;

    /** Values a realm name must match - mirrors the constraint of {@code TenantId}. */
    public static final String PATTERN_STRING = "^[a-z][a-z0-9_]*[a-z0-9]$";

    /** Shortest accepted realm name. */
    public static final int MIN_LENGTH = 2;

    /** Longest accepted realm name. */
    public static final int MAX_LENGTH = 10;

    private static final Pattern PATTERN = Pattern.compile(PATTERN_STRING);

    private final String value;

    /**
     * Constructor with the realm name.
     *
     * @param value Name of the tenant's Keycloak realm.
     *
     * @throws IllegalArgumentException The value is not a valid realm name.
     */
    public TenantRealmId(final String value) {
        super();
        Objects.requireNonNull(value, "value==null");
        if (!isValid(value)) {
            throw new IllegalArgumentException("The argument 'value' is not a valid realm name ("
                    + MIN_LENGTH + "-" + MAX_LENGTH + " characters matching " + PATTERN_STRING + "): '"
                    + value + "'");
        }
        this.value = value;
    }

    @Override
    public final String asBaseType() {
        return value;
    }

    /**
     * Returns the information if a given string can be converted into
     * an instance of TenantRealmId. A <code>null</code> value returns <code>true</code>.
     *
     * @param value
     *            Value to check.
     *
     * @return TRUE if it's a valid string, else FALSE.
     */
    public static boolean isValid(@Nullable final String value) {
        if (value == null) {
            return true;
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            return false;
        }
        return PATTERN.matcher(value).matches();
    }

    /**
     * Parses a given string and returns a new instance of TenantRealmId.
     *
     * @param value
     *            Value to convert. A <code>null</code> value returns
     *            <code>null</code>.
     *
     * @return Converted value.
     */
    @Nullable
    public static TenantRealmId valueOf(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        return new TenantRealmId(value);
    }

}
