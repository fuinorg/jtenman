package org.fuin.jtenman.shared.domain.tenants;

import java.io.Serial;
import java.util.Objects;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * An application that is not part of the configured catalogue was named. Which applications exist is deployment configuration, not domain state, so an unknown identifier is a mistake rather than something to record.
 */
public final class UnknownApplicationException extends Exception {

    @Serial
    private static final long serialVersionUID = 1000L;

    private String application;
    
    /**
     * Constructs a new instance of the exception.
     *
     * @param application The identifier that is not in the catalogue.
     */
    public UnknownApplicationException(final String application) {
        super(Objects.requireNonNull(KeyValueEL.replace("Unknown application '${application}' - it is not in the configured catalogue",  new KeyValue("application", application))));
        Contract.requireArgNotNull("application", application);
        
        this.application = application;
    }

    /**
     * Returns: The identifier that is not in the catalogue.
     *
     * @return Current value.
     */
    public final String getApplication() {
        return application;
    }
    
}
