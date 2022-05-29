package com.wealthpilot.quote.store.util;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import org.springframework.lang.Nullable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * A common base class for Entities that have a numeric ID.
 *
 * @author PhilippSommersguter
 */
@MappedSuperclass
@SuperBuilder(toBuilder = true, buildMethodName = "buildWithoutValidation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(generator = AssignedOrSequenceGenerator.NAME)
    @ToString.Include
    @Column(name = "id")
    @Getter
    @Setter
    @Nullable
    private Long id;

    /**
     * Override of the Lombok generated Builder in order to provide validation.
     *
     * @param <C> the entity class for which the builder is overwritten.
     * @param <B> the builder class.
     */
    @SuppressWarnings({ "java:S1610", "unused" }) // used by lombok
    public abstract static class BaseEntityBuilder<C extends BaseEntity, B extends BaseEntityBuilder<C, B>> implements ValidationSelfCheck<C> {
    }

    @Override
    @SuppressWarnings("java:S2097") // type test is there
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        //checking isAssignableFrom because other could be a HibernateProxy class
        if (getClass().isAssignableFrom(o.getClass())) {
            BaseEntity other = (BaseEntity) o;
            if (other.getId() == null || getId() == null) {
                return false;
            }
            return Objects.equals(getId(), other.getId());
        }
        if (o.getClass().isAssignableFrom(getClass())) {
            return o.equals(this);
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (id == null) {
            return super.hashCode();
        }
        return Objects.hashCode(id);
    }
}
