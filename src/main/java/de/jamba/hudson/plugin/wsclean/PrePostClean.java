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
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;

import org.apache.commons.lang.StringUtils;
import java.io.IOException;
import java.lang.Exception;
import java.lang.InterruptedException;
import java.lang.String;
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
					listener.getLogger().println("cleaning on " + normalizedName);
					deleteWorkspaceOn(project, listener, node, normalizedName);
				}
			}
		}
	}

	@SuppressWarnings("rawtypes")
	private void deleteWorkspaceOn(AbstractProject project, BuildListener listener, Node node, String nodeName) {
		FilePath fp = null;
		if (project instanceof TopLevelItem) {
			fp = node.getWorkspaceFor((TopLevelItem) project);
		}
		else if (project instanceof MatrixConfiguration) {
			fp = getWorkspaceFor((MatrixConfiguration) project, listener, node, nodeName);
		}
		else {
			listener.getLogger().println("Project is neither TopLevelItem nor MatrixConfiguration!? Cannot determine other workspaces!");
		}
		if (fp != null) {
			try {
				listener.getLogger().println("Deleting contents of " + fp);
				fp.deleteContents();
			} catch (IOException e) {
				listener.getLogger().println(
						"can't delete on node " + nodeName + "\n" + e.getMessage());
			} catch (InterruptedException e) {
				listener.getLogger().println(
						"can't delete on node " + nodeName + "\n" + e.getMessage());
			} catch (RequestAbortedException e){
				listener.getLogger().println(
						"can't delete on node " + nodeName + "\n" + e.getMessage());
			}
		} else {
			listener.getLogger().println(
					"No workspace found on " + nodeName + ". Node is maybe offline.");
		}
	}

	private FilePath getWorkspaceFor(MatrixConfiguration matrixConfig, BuildListener listener, Node node, String nodeName) {
		try {
			String someWorkspacePath = matrixConfig.getSomeWorkspace().getRemote();
			String matrixWorkspacePath = node.getWorkspaceFor((hudson.model.TopLevelItem) matrixConfig.getParent()).getRemote();
			String[] parts = matrixWorkspacePath.split("[\\\\/]");
			String[] configurationWorkspaceParts = someWorkspacePath.split(parts[parts.length - 2] + "[\\\\/]" + parts[parts.length - 1]);
			String separator = (String) node.toComputer().getSystemProperties().get("file.separator");
			String resultPath = matrixWorkspacePath + separator + configurationWorkspaceParts[configurationWorkspaceParts.length - 1];
			return new FilePath(node.getChannel(), StringUtils.join(resultPath.split("[\\\\/]"), separator));
		} catch (IOException e) {
			listener.getLogger().println("Failure: " + nodeName + "\n" + e.getMessage());
		} catch (InterruptedException e) {
			listener.getLogger().println("Failure: " + nodeName + "\n" + e.getMessage());
		} catch (NullPointerException e){
			listener.getLogger().println("Failure: " + nodeName + "\n" + e.getMessage());
		}
		return null;
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
