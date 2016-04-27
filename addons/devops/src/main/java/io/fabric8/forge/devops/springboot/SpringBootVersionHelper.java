/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.forge.devops.springboot;

import java.io.IOException;
import java.io.InputStream;

public class SpringBootVersionHelper {

    public static String getVersion(String name) {
        try (InputStream is = SpringBootVersionHelper.class.getResourceAsStream("/META-INF/maven/io.fabric8.forge/devops/pom.xml")) {
            String xml = IOHelper.loadText(is);
            String version = between(xml, "<" + name + ">", "</" + name + ">");
            return version;
        } catch (IOException e) {
            // ignore
        }
        return "";
    }

    /**
     * Returns the string after the given token
     *
     * @param text  the text
     * @param after the token
     * @return the text after the token, or <tt>null</tt> if text does not contain the token
     */
    public static String after(String text, String after) {
        if (!text.contains(after)) {
            return null;
        }
        return text.substring(text.indexOf(after) + after.length());
    }

    /**
     * Returns the string before the given token
     *
     * @param text  the text
     * @param before the token
     * @return the text before the token, or <tt>null</tt> if text does not contain the token
     */
    public static String before(String text, String before) {
        if (!text.contains(before)) {
            return null;
        }
        return text.substring(0, text.indexOf(before));
    }

    /**
     * Returns the string between the given tokens
     *
     * @param text  the text
     * @param after the before token
     * @param before the after token
     * @return the text between the tokens, or <tt>null</tt> if text does not contain the tokens
     */
    public static String between(String text, String after, String before) {
        text = after(text, after);
        if (text == null) {
            return null;
        }
        return before(text, before);
    }

}
