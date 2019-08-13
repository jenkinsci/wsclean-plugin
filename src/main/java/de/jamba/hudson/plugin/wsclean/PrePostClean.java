package de.jamba.hudson.plugin.wsclean;

import java.io.IOException;
import java.util.Set;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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

public class PrePostClean extends BuildWrapper {

    public boolean before;

    @SuppressWarnings("unused")
    @Deprecated
    private transient Boolean behind; // not used, but can appear in old configurations

    public PrePostClean() {
    }

    @DataBoundConstructor
    public PrePostClean(boolean before) {
        this();
        setBefore(before);
    }

    public boolean isBefore() {
        return before;
    }

    @DataBoundSetter
    public void setBefore(boolean before) {
        this.before = before;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        final boolean runAtStart = isBefore();
        final boolean runAtEnd = !runAtStart;
        // TearDown
        class TearDownImpl extends Environment {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                if (runAtEnd) {
                    executeOnSlaves(build, listener);
                }
                return super.tearDown(build, listener);
            }
        }

        if (runAtStart) {
            executeOnSlaves(build, listener);
        }
        return new TearDownImpl();
    }

    private void executeOnSlaves(AbstractBuild<?, ?> build, BuildListener listener) throws InterruptedException {
        listener.getLogger().println("Run PrePostClean");
        // select actual running label
        String runNode = build.getBuiltOnStr();

        listener.getLogger().println("Running on " + toNormalizedNodeName(runNode));

        AbstractProject<?, ?> project = build.getProject();
        Label assignedLabel = project.getAssignedLabel();
        if (assignedLabel == null) {
            listener.getLogger().println("Skipping roaming project.");
            return;
        }
        Set<Node> nodesForLabel = assignedLabel.getNodes();
        if (nodesForLabel != null) {
            for (Node node : nodesForLabel) {
                String nodeName = node.getNodeName();
                if (!runNode.equals(nodeName)) {
                    String normalizedName = toNormalizedNodeName(nodeName);
                    listener.getLogger().println("Cleaning on " + normalizedName);
                    deleteWorkspaceOn(project, listener, node, normalizedName);
                }
            }
        }
    }

    private void deleteWorkspaceOn(AbstractProject<?, ?> project, BuildListener listener, Node node, String nodeName)
            throws InterruptedException {
        if (project instanceof TopLevelItem) {
            FilePath fp = node.getWorkspaceFor((TopLevelItem) project);
            if (fp != null) {
                deleteWorkspaceOn(listener, nodeName, fp);
            } else {
                listener.getLogger().println("No workspace found on " + nodeName + ". Node is maybe offline.");
            }
        } else {
            listener.getLogger().println("Project is no TopLevelItem!? Cannot determine other workspaces!");
        }
    }

    /* This is only non-private for test purposes. */
    @Restricted(NoExternalUse.class) // unit-test only
    void deleteWorkspaceOn(BuildListener listener, String nodeName, FilePath fp) throws InterruptedException {
        try {
            fp.deleteContents();
        } catch (IOException | RequestAbortedException e) {
            listener.getLogger()
                    .println("Can't delete " + fp.getRemote() + " on node " + nodeName + "\n" + e.getMessage());
            listener.getLogger().print(e);
        }
    }

    private static String toNormalizedNodeName(String nodeName) {
        final String normalizedNodeName = (nodeName == null || "".equals(nodeName)) ? "master" : nodeName;
        return normalizedNodeName;
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        public DescriptorImpl() {
            super(PrePostClean.class);
        }

        @Override
        public String getDisplayName() {
            return Messages.PrePostClean_displayName();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }
    }
}
