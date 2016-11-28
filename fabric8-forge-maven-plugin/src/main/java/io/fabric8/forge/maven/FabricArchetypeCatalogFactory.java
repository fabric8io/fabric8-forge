/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.forge.maven;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.archetype.catalog.io.xpp3.ArchetypeCatalogXpp3Reader;
import org.apache.maven.artifact.Artifact;

public class FabricArchetypeCatalogFactory {

    public static ArchetypeCatalog getArchetypeCatalog(Artifact artifact) throws Exception {
        File file = artifact.getFile();
        if (file != null) {
            URL url = new URL("file", (String) null, file.getAbsolutePath());
            URLClassLoader loader = new URLClassLoader(new URL[]{url});
            InputStream is = loader.getResourceAsStream("archetype-catalog.xml");
            if (is != null) {
                return (new ArchetypeCatalogXpp3Reader()).read(is);
            }
        }
        return null;
    }
}
