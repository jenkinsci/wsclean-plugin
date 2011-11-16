package de.jamba.hudson.plugin.wsclean;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TopLevelItem;
import hudson.remoting.RequestAbortedException;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.util.Set;

import org.kohsuke.stapler.DataBoundConstructor;

public class PrePostClean extends BuildWrapper {

	public boolean before;
	private boolean behind;

	public boolean isBefore() {
		return before;
	}

	public void setBefore(boolean before) {
		this.before = before;
		this.behind = !before;
	}

	@DataBoundConstructor
	public PrePostClean(boolean before) {
		this.before = before;
		this.behind = !before;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {

		// TearDown
		class TearDownImpl extends Environment {

			@Override
			public boolean tearDown(AbstractBuild build, BuildListener listener)
					throws IOException, InterruptedException {
				if (behind) {
					executeOnSlaves(build, listener);
				}
				return super.tearDown(build, listener);
			}

		}

		if (before) {
			executeOnSlaves(build, listener);
		}
		return new TearDownImpl();

	}

	@SuppressWarnings("rawtypes")
	private void executeOnSlaves(AbstractBuild build, BuildListener listener) {
		listener.getLogger().println("run PrePostClean");
		// select actual running label
		String runNode = build.getBuiltOnStr();


		if (runNode.length() == 0) {
			listener.getLogger().println("running on master");
		} else {
			listener.getLogger().println("running on " + runNode);
		}

		AbstractProject project = build.getProject();
		Label assignedLabel = project.getAssignedLabel();
		if (assignedLabel == null) {
 			listener.getLogger().println("skipping roaming project.");
 			return;
        }
 		Set<Node> nodesForLabel = assignedLabel.getNodes();
		if (nodesForLabel != null) {
			for (Node node : nodesForLabel) {
				if (!runNode.equals(node.getNodeName())) {
					String normalizedName = "".equals(node.getNodeName()) ? "master" : node.getNodeName();
						listener.getLogger().println(
								"cleaning on " + normalizedName);
						deleteWorkspaceOn(project, listener, node, normalizedName);
				}

			}
		}
	}

	@SuppressWarnings("rawtypes")
	private void deleteWorkspaceOn(AbstractProject project, BuildListener listener, Node node, String nodeName) {
		if (project instanceof TopLevelItem) {
			FilePath fp = node.getWorkspaceFor((TopLevelItem) project);
			if (fp != null) {
				try {
					fp.deleteContents();
				} catch (IOException e) {
					listener.getLogger().println(
							"can't delete on node " + nodeName + "\n" + e.getMessage());
					listener.getLogger().print(e);
				} catch (InterruptedException e) {
					listener.getLogger().println(
							"can't delete on node " + nodeName + "\n" + e.getMessage());
					listener.getLogger().print(e);
				} catch (RequestAbortedException e){
					listener.getLogger().println(
							"can't delete on node " + nodeName + "\n" + e.getMessage());
				}
				
			} else {
				listener.getLogger().println(
						"No workspace found on " + nodeName + ". Node is maybe offline.");
			}
		} else {
			listener.getLogger().println("Project is no TopLevelItem!? Cannot determine other workspaces!");
		}
	}

	@Extension
	public static final class DescriptorImpl extends BuildWrapperDescriptor {
		public DescriptorImpl() {
			super(PrePostClean.class);
		}

		public String getDisplayName() {
			return "Clean up all workspaces of this job in the same slavegroup";
		}

		public boolean isApplicable(AbstractProject<?, ?> item) {
			return true;
		}

	}
}
