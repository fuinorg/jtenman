package org.fuin.jtenman.shared.domain.tenants;

import jakarta.annotation.Generated;
import jakarta.persistence.AttributeConverter;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;
import org.fuin.objects4j.common.AsStringCapable;
import org.fuin.objects4j.common.ConstraintViolationException;
import org.fuin.objects4j.common.HasPublicStaticIsValidMethod;
import org.fuin.objects4j.common.HasPublicStaticValueOfMethod;
import org.fuin.objects4j.common.ValueObjectWithBaseType;
import org.fuin.objects4j.core.Validators;
import org.jspecify.annotations.Nullable;

/**
 * The name of a tenant's Keycloak realm. <p> The constraints are <b>not</b> a style preference: the resource-server side derives an "org.fuin.ddd4j.core.TenantId" from the realm segment of the token issuer while decoding, and that type accepts 2-10 characters matching "^[a-z][a-z|0-9|_]*[a-z|0-9]$". A realm outside that range makes token decoding throw in every consuming application, so it is refused here - in the control plane, where a person sees the message.
 */
@Immutable
@Generated("Generated class - Manual changes will be overwritten")
@HasPublicStaticIsValidMethod
@HasPublicStaticValueOfMethod
public final class RealmName implements ValueObjectWithBaseType<String>, Comparable<RealmName>, Serializable, AsStringCapable {

    @Serial
    private static final long serialVersionUID = 1000L;

    @Size(min=2, max=10)
    @Pattern(regexp="^[a-z][a-z0-9_]*[a-z0-9]$")
    private String value;

    /**
     * Protected default constructor for deserialization.
     */
    @SuppressWarnings("NullAway.Init")
    protected RealmName() {
        super();
    }

    /**
     * Constructor with mandatory data.
     * 
     * @param value
     *            Value.
     */
    public RealmName(final String value) {
        super();
        requireArgValid("value", value);
        this.value = value;
    }

    @Override
    public String asBaseType() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public String asString() {
        return value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RealmName other = (RealmName) obj;
        return Objects.equals(value, other.value);
    }

    @Override
    public int compareTo(final RealmName other) {
        return value.compareTo(other.value);
    }

    @Override
    public Class<String> getBaseType() {
        return String.class;
    }

    /**
     * Verifies that a given string can be converted into the type.
     * 
     * @param value
     *            Value to validate.
     * 
     * @return Returns {@literal true} if it's a valid type else {@literal false}.
     */
    public static boolean isValid(final String value) {
        if (value == null) {
            return true;
        }
        return Validators.get().validateValue(RealmName.class, "value", value).isEmpty();
    }

    /**
     * Verifies if the argument is valid and throws an exception if this is not
     * the case.
     * 
     * @param name
     *            Name of the value for a possible error message.
     * @param value
     *            Value to check.
     * 
     * @throws ConstraintViolationException
     *             The value was not valid.
     */
    public static void requireArgValid(final String name, final String value) throws ConstraintViolationException {

        if (!isValid(value)) {
            throw new ConstraintViolationException("The argument '" + name
                    + "' is not valid: '" + value + "'");
        }

    }

    /**
     * Ensures that the string can be converted into the type.
     */
    @Target({ ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD,
            ElementType.ANNOTATION_TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = { Validator.class })
    @Documented
    public @interface RealmNameStr {

        String message()

        default "{org.fuin.jtenman.shared.domain.tenants.RealmName.message}";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

    }

    /**
     * Validates if a string is compliant with the type.
     */
    public static final class Validator implements
            ConstraintValidator<RealmNameStr, String> {

        @Override
        public void initialize(
                final RealmNameStr annotation) {
            // Not used
        }

        @Override
        public boolean isValid(final String value,
                final ConstraintValidatorContext context) {
            return RealmName.isValid(value);
        }

    }

    /**
     * Converts the value object from/to String.
     */
    public static final class Converter implements AttributeConverter<RealmName, String> {

        // General methods

        /**
         * Converts the String into a RealmName. A {@literal null} parameter will return {@literal null}.
         * 
         * @param value
         *            String to convert into a RealmName.
         * 
         * @return Value object of type RealmName.
         */
        @Nullable
        public RealmName toVO(@Nullable final String value) {
            if (value == null) {
                return null;
            }
            return new RealmName(value);
        }

        /**
         * Converts a RealmName into a String. A {@literal null} parameter will return {@literal null}.
         * 
         * @param value
         *            Value object of type RealmName.
         * 
         * @return String.
         */
        @Nullable
        public String fromVO(@Nullable final RealmName value) {
            if (value == null) {
                return null;
            }
            return value.asBaseType();
        }

        // JPA Attribute Converter

        @Override
        @Nullable
        public String convertToDatabaseColumn(@Nullable final RealmName obj) {
            return fromVO(obj);
        }

        @Override
        @Nullable
        public RealmName convertToEntityAttribute(@Nullable final String value) {
            return toVO(value);
        }

    }

}
