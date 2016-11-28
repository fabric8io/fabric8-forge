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
package io.fabric8.forge.rest.main;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.maven.archetype.catalog.Archetype;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.DependencyQuery;
import org.jboss.forge.addon.dependencies.DependencyResolver;
import org.jboss.forge.addon.dependencies.builder.DependencyQueryBuilder;
import org.jboss.forge.addon.maven.archetype.ArchetypeCatalogFactory;
import org.jboss.forge.addon.maven.archetype.ArchetypeCatalogFactoryRegistry;
import org.jboss.forge.furnace.Furnace;
import org.jboss.forge.furnace.addons.AddonRegistry;

@ApplicationScoped
@Deprecated
public class ArchetypesCatalogService {

    @Inject
    Furnace furnace;

    @Inject
    DependencyResolver resolver;

    public Map<String, Set<String>> getArchetypeCatalogs() {
        System.out.println("Downloading Maven archetypes +++ start +++");

        Map<String, Set<String>> catalogs = new LinkedHashMap();

        AddonRegistry addonRegistry = furnace.getAddonRegistry();
        ArchetypeCatalogFactoryRegistry factory = addonRegistry.getServices(ArchetypeCatalogFactoryRegistry.class).get();
        if (factory != null) {
            Iterable<ArchetypeCatalogFactory> it = factory.getArchetypeCatalogFactories();
            for (ArchetypeCatalogFactory cat : it) {
                System.out.println("Found ArchetypeCatalog: " + cat.getName());

                Set<String> coords = new LinkedHashSet<>();
                catalogs.put(cat.getName(), coords);

                List<Archetype> archetypes = cat.getArchetypeCatalog().getArchetypes();
                for (Archetype arc : archetypes) {
                    String coord = arc.getGroupId() + ":" + arc.getArtifactId() + ":" + arc.getVersion();
                    DependencyQuery dq = DependencyQueryBuilder.create(coord);
                    try {
                        System.out.println("Downloading Maven archetype: " + coord);
                        Dependency dep = resolver.resolveArtifact(dq);

                        // remember archetype
                        coords.add(coord);

                    } catch (Throwable e) {
                        System.err.println("Cannot download Maven archetype: " + coord + " due " + e.getMessage());
                    }
                }
            }
        }
        System.out.println("Downloading Maven archetypes +++ end +++");

        return catalogs;
    }
}
