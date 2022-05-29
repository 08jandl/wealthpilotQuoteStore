package com.wealthpilot.quote.store.util;

import java.io.Serializable;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.metadata.ClassMetadata;

/**
 * ID-Generator implementation that will use an existing (assigned) id if present, and fall-back to SequenceStyleGenerator
 * (autoincrement) semantics if no id is present for a given entity instance.
 *
 * @author florian.kirchmeir
 */
public class AssignedOrSequenceGenerator extends SequenceStyleGenerator {

    public static final String NAME = "assignedOrSequence";

    @Override
    public Serializable generate(SharedSessionContractImplementor sharedSessionContractImplementor, Object object) {
        ClassMetadata classMetadata = sharedSessionContractImplementor.getEntityPersister(null, object).getClassMetadata();
        Serializable id = classMetadata.getIdentifier(object, sharedSessionContractImplementor);
        return id != null ? id : super.generate(sharedSessionContractImplementor, object);
    }
}
