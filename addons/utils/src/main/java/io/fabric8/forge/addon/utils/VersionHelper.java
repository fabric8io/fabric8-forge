/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.forge.addon.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import io.fabric8.utils.Strings;

public final class VersionHelper {

    public static final String ENV_FABRIC8_ARCHETYPES_VERSION = "FABRIC8_ARCHETYPES_VERSION";

    /**
     * Retrieves the version of fabric8 to use
     */
    public static String fabric8Version() {
        return MavenHelpers.getVersion("io.fabric8", "fabric8-maven-plugin");
    }

    /**
     * Returns the version to use for the fabric8 archetypes
     */
    public static String fabric8ArchetypesVersion() {
        String version = System.getenv(ENV_FABRIC8_ARCHETYPES_VERSION);
        if (Strings.isNotBlank(version)) {
            return version;
        }
        return MavenHelpers.getVersion("io.fabric8.archetypes", "archetypes-catalog");
    }

    /**
     * Retrieves the version of hawtio to use
     */
    public static String hawtioVersion() {
        return MavenHelpers.getVersion("io.hawt", "hawtio-maven-plugin");
    }

    /**
     * Retrieves the version of docker to use
     */
    public static String dockerVersion() {
        return MavenHelpers.getVersion("org.jolokia", "docker-maven-plugin");
    }

    public static String after(String text, String after) {
        if (!text.contains(after)) {
            return null;
        }
        return text.substring(text.indexOf(after) + after.length());
    }

    public static String before(String text, String before) {
        if (!text.contains(before)) {
            return null;
        }
        return text.substring(0, text.indexOf(before));
    }

    public static String between(String text, String after, String before) {
        text = after(text, after);
        if (text == null) {
            return null;
        }
        return before(text, before);
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line
     * terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    public static String loadText(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        InputStreamReader isr = new InputStreamReader(in);
        try {
            BufferedReader reader = new BufferedReader(isr);
            while (true) {
                String line = reader.readLine();
                if (line != null) {
                    builder.append(line);
                    builder.append("\n");
                } else {
                    break;
                }
            }
            return builder.toString();
        } finally {
            try {
                isr.close();
            } catch (Exception e) {
                // ignore
            }
            try {
                in.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
