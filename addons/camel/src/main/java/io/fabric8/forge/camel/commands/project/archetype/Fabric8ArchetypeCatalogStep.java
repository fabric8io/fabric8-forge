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
package io.fabric8.forge.camel.commands.project.archetype;

import com.google.common.base.Objects;
import io.fabric8.forge.addon.utils.VersionHelper;
import io.fabric8.forge.addon.utils.archetype.FabricArchetypeCatalogFactory;
import io.fabric8.utils.Strings;
import org.apache.maven.archetype.catalog.Archetype;
import org.jboss.forge.addon.maven.projects.archetype.ui.ConstantArchetypeSelectionWizardStep;

import javax.inject.Inject;
import java.util.List;

/**
 */
public abstract class Fabric8ArchetypeCatalogStep extends ConstantArchetypeSelectionWizardStep {

    @Inject
    private FabricArchetypeCatalogFactory catalogFactory;

    public Fabric8ArchetypeCatalogStep() {
        setArchetypeGroupId("io.fabric8.archetypes");
    }

    @Override
    protected void setArchetypeArtifactId(String archetypeArtifactId) {
        super.setArchetypeArtifactId(archetypeArtifactId);
        if (Strings.isNotBlank(archetypeArtifactId)) {
            if (Strings.isNullOrBlank(getArchetypeVersion())) {
                String version = null;
                if (catalogFactory != null) {
                    List<Archetype> archetypes = catalogFactory.getArchetypeCatalog().getArchetypes();
                    for (Archetype archetype : archetypes) {
                        if (Objects.equal(archetype.getArtifactId(), archetypeArtifactId)) {
                            version = archetype.getVersion();
                        }
                    }
                }
                if (Strings.isNullOrBlank(version)) {
                    version = VersionHelper.fabric8ArchetypesVersion();
                }
                if (version != null) {
                    setArchetypeVersion(version);
                } else {
                    throw new IllegalArgumentException("Could not find an archetype for id " + archetypeArtifactId
                            + " in the archetype catalog " + catalogFactory + " or find version for environment variable " + VersionHelper.ENV_FABRIC8_ARCHETYPES_VERSION);
                }
            }
        }
    }
}
