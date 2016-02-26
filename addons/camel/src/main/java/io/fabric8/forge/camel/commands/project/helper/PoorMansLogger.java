/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p/>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.forge.camel.commands.project.helper;

import java.io.File;
import java.io.FileOutputStream;

/**
 * A poor mans logger that logs to the file <tt>HOME/fabric8-camel.log</tt>, in the home directory.
 */
public final class PoorMansLogger {

    private File file;
    private FileOutputStream fos;

    public PoorMansLogger() {
        try {
            String home = System.getenv("HOME");
            if (home == null) {
                home = ".";
            }
            file = new File(home + "/fabric8-camel.log");
            fos = new FileOutputStream(file, true);
            info("PoorMansLogger created");
        } catch (Exception e) {
            // ignore
        }
    }

    public void info(String message) {
        try {
            if (fos != null) {
                fos.write(message.getBytes());
                fos.write(System.lineSeparator().getBytes());
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
