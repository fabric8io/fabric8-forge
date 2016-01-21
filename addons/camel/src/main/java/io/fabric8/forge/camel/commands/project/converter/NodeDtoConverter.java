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
package io.fabric8.forge.camel.commands.project.converter;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.fabric8.forge.camel.commands.project.dto.ContextDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDto;
import io.fabric8.forge.camel.commands.project.dto.NodeDtos;
import io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.input.UISelectOne;

public class NodeDtoConverter implements Converter<String, NodeDto> {
    private static final Logger LOG = Logger.getLogger(NodeDtoConverter.class.getName());

    private final Project project;
    private final UIContext context;
    private final UISelectOne<String> xml;

    public NodeDtoConverter(Project project, UIContext context, UISelectOne<String> xml) {
        this.project = project;
        this.context = context;
        this.xml = xml;
    }

    @Override
    public NodeDto convert(String name) {
        String xmlResourceName = xml.getValue();
        NodeDto answer = null;
        try {
            List<ContextDto> camelContexts = CamelXmlHelper.loadCamelContext(context, project, xmlResourceName);
            answer = NodeDtos.findNodeByKey(camelContexts, name);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Error converting to NodeDto due " + e.getMessage(), e);
        }
        return answer;
    }

}
