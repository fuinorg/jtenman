package org.fuin.jtenman.shared;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Carries the JSON decisions that apply to this project as a whole.
 * <p>
 * The identifiers and value objects of this project used to be registered here one by one - 76 lines
 * that had to be extended by hand whenever a model grew a value object, and where a forgotten entry was
 * not a compile error but a runtime failure. They are gone: {@code JandexJacksonModule} finds every value
 * object by scanning the Jandex index, and {@code Ddd4JacksonModule} registers a pair for every
 * identifier its {@code EntityIdFactory} knows. Both are wired in {@code EventStoreAutoConfiguration},
 * so a new value object is picked up without anyone having to remember this class.
 * <p>
 * What is left is the one rule that is a decision rather than a mechanical mapping - and the place to put
 * the next one.
 */
public class JtenmanJacksonModule extends Module {

    @Override
    public String getModuleName() {
        return "JtenmanJacksonModule";
    }

    @Override
    public void setupModule(final SetupContext context) {

        final SimpleSerializers serializers = new SimpleSerializers();
        // A long exceeds the exact integer range of a JavaScript number, so it is written as a string
        // rather than risking a silently rounded amount in a browser client. Jackson coerces it back.
        // This reaches the value objects wrapping a long as well: the generic serializer hands the base
        // value to the mapper instead of writing it itself, so an amount in minor units is a string too.
        serializers.addSerializer(Long.class, ToStringSerializer.instance);
        serializers.addSerializer(Long.TYPE, ToStringSerializer.instance);
        context.addSerializers(serializers);

    }

    @Override
    public Version version() {
        return new Version(1, 0, 0, "SNAPSHOT", "org.fuin", "jtenman-shared");
    }

}
