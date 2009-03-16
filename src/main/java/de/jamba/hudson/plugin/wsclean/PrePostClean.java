package de.jamba.hudson.plugin.wsclean;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.remoting.RequestAbortedException;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.File;
import java.io.IOException;

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

	@SuppressWarnings("unchecked")
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

	@SuppressWarnings("unchecked")
	private void executeOnSlaves(AbstractBuild build, BuildListener listener) {
		listener.getLogger().println("run PrePostClean");
		// select actual running label
		String runNode = build.getBuiltOnStr();


		if (runNode.length() == 0) {
			listener.getLogger().println("running on master");
		} else {
			listener.getLogger().println("running on " + runNode);

		}

		for (Node node : build.getProject().getAssignedLabel().getNodes()) {
			if (!runNode.equals(node.getNodeName()) ) {

				if (node.getNodeName().length() == 0) {
					listener.getLogger().println("clean on master");
					deleteOnMaster(build, listener);
				} else {
					listener.getLogger().println("clean on " + node.getNodeName());
					deleteRemote(build, listener, node);
				}
			}

		}
	}

	@SuppressWarnings("unchecked")
	private void deleteOnMaster(AbstractBuild build, BuildListener listener) {
		if (((Node) Hudson.getInstance()).getNumExecutors() > 0) {
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

	@SuppressWarnings("unchecked")
	private void deleteRemote(AbstractBuild build, BuildListener listener,
			Node node) {
		VirtualChannel vc = ((Slave) node).getComputer().getChannel();
		if (!((Slave) node).getComputer().isConnecting()
				&& !((Slave) node).getComputer().isTemporarilyOffline()) {
			((Slave) node).getComputer().connect(true);
			int i = 0;
			while (vc == null && ++i < 120) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					listener.getLogger().println(e.getMessage());
				}
				vc = ((Slave) node).getComputer().getChannel();
			}

		}
		if (vc != null) {
			FilePath fp = new FilePath(vc, ((Slave) node).getRemoteFS()
					+ "/workspace/" + build.getProject().getName());
			try {
				fp.deleteContents();
			} catch (IOException e) {
				listener.getLogger().println(
						"cat delete on Slave " + e.getMessage());
				listener.getLogger().print(e);
			} catch (InterruptedException e) {
				listener.getLogger().println(
						"cat delete on Slave " + e.getMessage());
				listener.getLogger().print(e);
			} catch (RequestAbortedException e){
				listener.getLogger().println(
						"cat delete on Slave " + e.getMessage());
			}
			
		} else {
			listener.getLogger().println(
					"no deleteChannel on " + node.getNodeName());
		}
	}

	public Descriptor<BuildWrapper> getDescriptor() {
		return DESCRIPTOR;
	}

	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	public static final class DescriptorImpl extends BuildWrapperDescriptor {
		DescriptorImpl() {
			super(PrePostClean.class);
		}

		public String getDisplayName() {
			return "CleanUp all other workspaces in the same slavegroup";
		}

		public boolean isApplicable(AbstractProject<?, ?> item) {
			return true;
		}

	}
}
