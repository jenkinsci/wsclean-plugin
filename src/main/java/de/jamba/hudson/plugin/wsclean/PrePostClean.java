package de.jamba.hudson.plugin.wsclean;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.remoting.RequestAbortedException;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.File;
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

		Label assignedLabel = build.getProject().getAssignedLabel();
		if (assignedLabel == null) {
 			listener.getLogger().println("skipping roaming project.");
 			return;
                }
 		Set<Node> usedNodes = assignedLabel.getNodes();
		if (usedNodes != null) {
			for (Node node : usedNodes) {
				if (!runNode.equals(node.getNodeName())) {

					if (node.getNodeName().length() == 0) {
						listener.getLogger().println("clean on master");
						deleteOnMaster(build, listener);
					} else {
						listener.getLogger().println(
								"clean on " + node.getNodeName());
						deleteRemote(build, listener, (Slave)node);
					}
				}

			}
		}
	}

	@SuppressWarnings("rawtypes")
	private void deleteOnMaster(AbstractBuild build, BuildListener listener) {
		if (Hudson.getInstance().getNumExecutors() > 0) {
			FilePath fp = new FilePath(new File(Hudson.getInstance()
					.getRootPath()
					+ "/jobs/" + build.getProject().getName() + "/workspace"));
			try {
				fp.deleteContents();
			} catch (IOException e) {
				listener.getLogger().println(
						"cat delete on Master " + e.getMessage());
				listener.getLogger().print(e);
			} catch (InterruptedException e) {
				listener.getLogger().println(
						"cat delete on Master " + e.getMessage());
				listener.getLogger().print(e);
			}
		}
	}

	@SuppressWarnings("rawtypes")
	private void deleteRemote(AbstractBuild build, BuildListener listener,
			Slave slave) {
		VirtualChannel vc = slave.getComputer().getChannel();
//		if (!((Slave) node).getComputer().isConnecting()
//				&& !((Slave) node).getComputer().isTemporarilyOffline()) {
//			((Slave) node).getComputer().connect(true);
//			int i = 0;
//			while (vc == null && ++i < 120) {
//				try {
//					Thread.sleep(1000);
//				} catch (InterruptedException e) {
//					listener.getLogger().println(e.getMessage());
//				}
//				vc = ((Slave) node).getComputer().getChannel();
//			}
//
//		}
		if (vc != null) {
			FilePath fp = new FilePath(vc, slave.getRemoteFS()
					+ "/workspace/" + build.getProject().getName());
			try {
				fp.deleteContents();
			} catch (IOException e) {
				listener.getLogger().println(
						"can't delete on Slave " + slave.getNodeName() + "\n" + e.getMessage());
				listener.getLogger().print(e);
			} catch (InterruptedException e) {
				listener.getLogger().println(
						"can't delete on Slave " + slave.getNodeName() + "\n" + e.getMessage());
				listener.getLogger().print(e);
			} catch (RequestAbortedException e){
				listener.getLogger().println(
						"can't delete on Slave " + slave.getNodeName() + "\n" + e.getMessage());
			}
			
		} else {
			listener.getLogger().println(
					"no deleteChannel on " + slave.getNodeName());
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
