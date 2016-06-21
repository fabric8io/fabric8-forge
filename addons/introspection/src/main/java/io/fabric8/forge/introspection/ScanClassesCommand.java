package io.fabric8.forge.introspection;

import io.fabric8.forge.addon.utils.CommandHelpers;
import io.fabric8.forge.introspection.introspect.support.ClassScanner;
import org.jboss.forge.addon.facets.Faceted;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.parser.java.resources.JavaResource;
import org.jboss.forge.addon.parser.java.resources.JavaResourceVisitor;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.building.BuildException;
import org.jboss.forge.addon.projects.building.ProjectBuilder;
import org.jboss.forge.addon.projects.facets.ClassLoaderFacet;
import org.jboss.forge.addon.projects.facets.PackagingFacet;
import org.jboss.forge.addon.resource.ResourceFacet;
import org.jboss.forge.addon.resource.visit.VisitContext;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import static io.fabric8.forge.addon.utils.OutputFormatHelper.toJson;

/**
 *  Simple command to directly invoke the class scanner to find/complete class names
 */
public class ScanClassesCommand extends AbstractIntrospectionCommand {

	@Inject
	@WithAttributes(label = "search", required = false, description = "Search term to find classes in the project")
	private UIInput<String> search;

	private List<InputComponent> inputComponents;

	@Override
	public void initializeUI(UIBuilder builder) throws Exception {
		inputComponents = CommandHelpers.addInputComponents(builder, search);
	}

	@Override
	public UICommandMetadata getMetadata(UIContext context)
	{
		return Metadata
				.forCommand(getClass())
				.name("Introspector: Scan classes")
				.description("Find/filter available classes in the project")
				.category(Categories.create("Introspector"));
	}

	@Override
	public Result execute(UIExecutionContext uiExecutionContext) throws Exception {
		CommandHelpers.putComponentValuesInAttributeMap(uiExecutionContext, inputComponents);
		UIContext uiContext = uiExecutionContext.getUIContext();
		Map<Object, Object> map = uiContext.getAttributeMap();
		String search = (String) (map.get("search") != null ? map.get("search") : "");
		Project project = getSelectedProject(uiContext);
		ClassScanner scanner = ClassScanner.newInstance(project);
		final SortedSet<String> answer = scanner.findClassNames(search, 0);
		scanner.dispose();
		return Results.success(toJson(answer));
	}
}
