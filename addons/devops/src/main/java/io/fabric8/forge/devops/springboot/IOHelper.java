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

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IOHelper {

    public static void copyAndCloseInput(InputStream input, OutputStream output) throws IOException {
        copyAndCloseInput(input, output, 8192);
    }

    public static void copyAndCloseInput(InputStream input, OutputStream output, int bufferSize) throws IOException {
        copy(input, output, bufferSize, false);
        close(input);
    }

    public static int copy(final InputStream input, final OutputStream output, int bufferSize, boolean flushOnEachWrite) throws IOException {
        if (input instanceof ByteArrayInputStream) {
            // optimized for byte array as we only need the max size it can be
            input.mark(0);
            input.reset();
            bufferSize = input.available();
        } else {
            int avail = input.available();
            if (avail > bufferSize) {
                bufferSize = avail;
            }
        }

        if (bufferSize > 262144) {
            // upper cap to avoid buffers too big
            bufferSize = 262144;
        }

        final byte[] buffer = new byte[bufferSize];
        int n = input.read(buffer);
        int total = 0;
        while (-1 != n) {
            output.write(buffer, 0, n);
            if (flushOnEachWrite) {
                output.flush();
            }
            total += n;
            n = input.read(buffer);
        }
        if (!flushOnEachWrite) {
            // flush at end, if we didn't do it during the writing
            output.flush();
        }
        return total;
    }

    /**
     * Closes the given resource if it is available, logging any closing exceptions to the given log.
     *
     * @param closeable the object to close
     */
    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }


}
