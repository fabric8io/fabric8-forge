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
package io.fabric8.forge.rest.client;

import static io.fabric8.forge.rest.client.CommandConstants.ProjectNew;
import static io.fabric8.forge.rest.client.CommandConstants.ProjectNewProperties.Catalog.Fabric8;
import static io.fabric8.forge.rest.client.CommandConstants.ProjectNewProperties.Type.FromArchetypeCatalog;
import static io.fabric8.forge.rest.client.ForgeClientAsserts.assertChooseValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import io.fabric8.forge.rest.dto.PropertyDTO;
import io.fabric8.utils.Strings;

/**
 */
public class CreateQuickstartProjectKT extends ForgeTestSupport {
    private static final transient Logger LOG = LoggerFactory.getLogger(CreateQuickstartProjectKT.class);

    /**
     * TODO posting the archetype catalog generates:
     * <p>
     * java.lang.ClassCastException: org.jboss.forge.furnace.proxy.ForgeProxy_$$_javassist_4511e8db-4647-48b3-aa6c-b5ba9f325205 cannot be cast to org.jboss.forge.addon.maven.archetype.ArchetypeCatalogFactory
     * at org.jboss.forge.addon.maven.projects.archetype.ui.ArchetypeCatalogSelectionWizardStep$1.convert(ArchetypeCatalogSelectionWizardStep.java:79)
     * at sun.reflect.GeneratedMethodAccessor586.invoke(Unknown Source)
     * at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)[:1.8.0_101]
     * at java.lang.reflect.Method.invoke(Method.java:498)[:1.8.0_101]
     * at org.jboss.forge.furnace.proxy.ClassLoaderAdapterCallback$2.call(ClassLoaderAdapterCallback.java:124)[furnace-proxy-2.24.2.Final.jar:2.24.2.Final]
     * at org.jboss.forge.furnace.util.ClassLoaders.executeIn(ClassLoaders.java:42)[furnace-api-2.24.2.Final.jar:2.24.2.Final]
     * at org.jboss.forge.furnace.proxy.ClassLoaderAdapterCallback.invoke(ClassLoaderAdapterCallback.java:97)[furnace-proxy-2.24.2.Final.jar:2.24.2.Final]
     * at org.jboss.forge.addon.convert.Converter_$$_javassist_f8ce79f6-7527-458e-8b8d-37ea1872b873.convert(Converter_$$_javassist_f8ce79f6-7527-458e-8b8d-37ea1872b873.java)[convert-api-3.3.3.Final.jar:3.3.3.Final]
     * at org.jboss.forge.addon.ui.util.InputComponents.convertToUIInputValue(InputComponents.java:205)[ui-api-3.3.3.Final.jar:3.3.3.Final]
     * at org.jboss.forge.addon.ui.util.InputComponents.setSingleInputValue(InputComponents.java:118)[ui-api-3.3.3.Final.jar:3.3.3.Final]
     * at org.jboss.forge.addon.ui.util.InputComponents.setValueFor(InputComponents.java:84)[ui-api-3.3.3.Final.jar:3.3.3.Final]
     * at io.fabric8.forge.rest.dto.UICommands.populateController(UICommands.java:337)[fabric8-forge-core-2.3.77.jar:2.3.77]
     */
    @Ignore
    public void testNewArchetypeProject() throws Exception {
        String projectName = generateProjectName("qs");

        String commandName = ProjectNew;

        ValueProvider valueProvider = new ValueProvider() {
            @Override
            public Object getValue(String propertyName, PropertyDTO property, int pageNumber) {
                switch (pageNumber) {
                    case 0:
                        switch (propertyName) {
                            case "named":
                                return projectName;
                            case "targetLocation":
                                return null;
                            case "type":
                                return assertChooseValue(propertyName, property, pageNumber, FromArchetypeCatalog);
                        }
                        break;

                    case 1:
                        switch (propertyName) {
                            case "catalog":
                                return assertChooseValue(propertyName, property, pageNumber, Fabric8);
                        }
                        break;
                }
                return super.getValue(propertyName, property, pageNumber);

            }
        };

        executeWizardCommand(projectName, commandName, valueProvider, 3);
    }


    @Ignore
    public void testListCommands() throws Exception {
        List<String> commandNames = forgeClient.getCommandNames();
        LOG.info("Command names: " + commandNames);
    }    
    
    @Ignore
    public void testQuickstartArchetypeProject() throws Exception {
    	String projectName = generateProjectName("qs");
        
        ValueProvider valueProvider = new ValueProvider() {
            @Override
            public Object getValue(String propertyName, PropertyDTO property, int pageNumber) {
                switch (pageNumber) {
                    case 0:
                        switch (propertyName) {
                            case "named":
                                return projectName;
                            case "targetLocation":
                                return null;
                            case "type":
                                return assertChooseValue(propertyName, property, pageNumber, FromArchetypeCatalog);
                        }
                        break;

                    case 1:
                        switch (propertyName) {
                            case "catalog":
                                return assertChooseValue(propertyName, property, pageNumber, Fabric8);
                        }
                        break;
                }
                return super.getValue(propertyName, property, pageNumber);

            }
        };
    	
        List<String> archetypes = getArchetypesFromJar();
        LOG.info("Archetypes names: " + archetypes);
    
        assertThat(archetypes).describedAs("Archetypes to create").isNotEmpty();

        for (String archetype: archetypes) {
            executeWizardCommand(projectName, archetype, valueProvider, 3);
        }
        	
    }
    
    protected List<String> getArchetypesFromJar() throws IOException {
        String entryName = "archetype-catalog.xml";
        URL url = getClass().getClassLoader().getResource(entryName);
        assertThat(url).describedAs("Could not find resource " + entryName + " on the classpath!").isNotNull();

        SortedSet<String> artifactIds = new TreeSet<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource is = new InputSource(url.openStream());
            Document doc = builder.parse(is);
            NodeList artifactTags = doc.getElementsByTagName("artifactId");
            for (int i = 0, size = artifactTags.getLength(); i < size; i++) {
                Node element = artifactTags.item(i);
                String artifactId = element.getTextContent();
                if (Strings.isNotBlank(artifactId)) {
                    artifactIds.add(artifactId);
                }
            }
        } catch (Exception e) {
            fail("Failed to parse " + entryName + " in catalog " + url + ". Exception " + e, e);
        }
        return new ArrayList<>(artifactIds);
    }

}
