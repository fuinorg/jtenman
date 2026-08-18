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
