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
package io.fabric8.forge.addon.utils;

import io.fabric8.utils.Strings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 */
public class Files {
    public static String joinPaths(String parentPath, String name) {
        String path = name;
        if (Strings.isNotBlank(parentPath)) {
            String separator = path.endsWith("/") || name.startsWith("/") ? "" : "/";
            path = parentPath + separator + name;
        }
        return path;
    }


    /**
     * Copy the source {@link File} to the target {@link File}.
     *
     * // TODO DELETEME when fabric8-utils released with this method
     */
    public static void copy(File source, File target) throws IOException {
        if (!source.exists()) {
            throw new FileNotFoundException("Source file not found:" + source.getAbsolutePath());
        }

        if (!target.exists() && !target.getParentFile().exists() && !target.getParentFile().mkdirs()) {
            throw new IOException("Can't create target directory:" + target.getParentFile().getAbsolutePath());
        }
        if (source.isDirectory()) {
            target.mkdirs();
            File[] files = source.listFiles();
            if (files != null) {
                for (File child : files) {
                    copy(child, new File(target, child.getName()));
                }
            }
        } else {
            FileInputStream is = new FileInputStream(source);
            FileOutputStream os = new FileOutputStream(target);
            io.fabric8.utils.Files.copy(is, os);
        }
    }
}
