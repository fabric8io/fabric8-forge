package io.fabric8.forge.introspection;

import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.ui.AbstractProjectCommand;
import org.jboss.forge.addon.ui.UIProvider;
import org.jboss.forge.addon.ui.command.UICommand;

import javax.inject.Inject;

/**
 * An abstract base class for introspection related commands
 */
public abstract class AbstractIntrospectionCommand extends AbstractProjectCommand implements UICommand {

	public static final int ROOT_LEVEL = 1;
	public static String CATEGORY = "Introspection";

	@Inject
	private ProjectFactory projectFactory;
	UIProvider uiProvider;

	@Override
	protected boolean isProjectRequired() { return false; }

	@Override
	protected ProjectFactory getProjectFactory() { return projectFactory; }

	public void setUiProvider(UIProvider uiProvider) {
		this.uiProvider = uiProvider;
	}
}
