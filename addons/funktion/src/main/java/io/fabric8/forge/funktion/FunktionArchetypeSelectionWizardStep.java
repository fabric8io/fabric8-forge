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
package io.fabric8.forge.funktion;

import io.fabric8.forge.addon.utils.archetype.FabricArchetypeCatalogFactory;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.DependencyRepository;
import org.jboss.forge.addon.dependencies.DependencyResolver;
import org.jboss.forge.addon.dependencies.builder.DependencyQueryBuilder;
import org.jboss.forge.addon.maven.projects.archetype.ArchetypeHelper;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.facets.MetadataFacet;
import org.jboss.forge.addon.resource.DirectoryResource;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.ui.command.AbstractUICommand;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.InputComponentFactory;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.jboss.forge.furnace.util.Strings;

import javax.inject.Inject;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;


/**
 */
public class FunktionArchetypeSelectionWizardStep extends AbstractUICommand implements UIWizardStep {
    @Inject
    private FabricArchetypeCatalogFactory catalogFactory;

    @Inject
    private DependencyResolver dependencyResolver;

    private UISelectOne<Archetype> archetype;

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        return null;
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(getClass()).name("Funktion: Choose Archetype")
                .description("Choose a Funktion archetype for your project");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        InputComponentFactory factory = builder.getInputComponentFactory();

        // List of Archetypes
        archetype = factory.createSelectOne("archetype", Archetype.class)
                .setLabel("Archetype")
                .setRequired(true).setItemLabelConverter(new Converter<Archetype, String>() {
                    @Override
                    public String convert(Archetype source) {
                        if (source == null) {
                            return null;
                        }
                        return source.getGroupId() + ":" + source.getArtifactId() + ":" + source.getVersion();
                    }
                }).setValueChoices(new Callable<Iterable<Archetype>>() {
                    @Override
                    public Iterable<Archetype> call() throws Exception {
                        Set<Archetype> result = new LinkedHashSet<>();
                        ArchetypeCatalog archetypes = catalogFactory.getArchetypeCatalog();
                        if (archetypes != null) {
                            List<Archetype> list = archetypes.getArchetypes();
                            for (Archetype archetype : list) {
                                if (isFunktionArchetype(archetype)) {
                                    result.add(archetype);
                                }
                            }
                        }
                        return result;
                    }
                }).setDescription(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        Archetype value = archetype.getValue();
                        return value == null ? null : value.getDescription();
                    }
                });
        builder.add(archetype);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        UIContext uiContext = context.getUIContext();
        Project project = (Project) uiContext.getAttributeMap().get(Project.class);
        Archetype chosenArchetype = archetype.getValue();
        String coordinate = chosenArchetype.getGroupId() + ":" + chosenArchetype.getArtifactId() + ":"
                + chosenArchetype.getVersion();
        DependencyQueryBuilder depQuery = DependencyQueryBuilder.create(coordinate);
        String repository = chosenArchetype.getRepository();
        if (!Strings.isNullOrEmpty(repository)) {
            if (repository.endsWith(".xml")) {
                int lastRepositoryPath = repository.lastIndexOf('/');
                if (lastRepositoryPath > -1)
                    repository = repository.substring(0, lastRepositoryPath);
            }
            if (!repository.isEmpty()) {
                depQuery.setRepositories(new DependencyRepository("archetype", repository));
            }
        }
        Dependency resolvedArtifact = dependencyResolver.resolveArtifact(depQuery);
        FileResource<?> artifact = resolvedArtifact.getArtifact();
        MetadataFacet metadataFacet = project.getFacet(MetadataFacet.class);
        File fileRoot = project.getRoot().reify(DirectoryResource.class).getUnderlyingResourceObject();
        ArchetypeHelper archetypeHelper = new ArchetypeHelper(artifact.getResourceInputStream(), fileRoot,
                metadataFacet.getProjectGroupName(), metadataFacet.getProjectName(), metadataFacet.getProjectVersion());
        JavaSourceFacet facet = project.getFacet(JavaSourceFacet.class);
        archetypeHelper.setPackageName(facet.getBasePackage());
        archetypeHelper.execute();
        return Results.success();
    }


    protected boolean isFunktionArchetype(Archetype archetype) {
        String artifactId = archetype.getArtifactId();
        return artifactId != null && artifactId.contains("funktion");
    }
}
