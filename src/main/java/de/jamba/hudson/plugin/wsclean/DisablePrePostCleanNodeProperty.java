package de.jamba.hudson.plugin.wsclean;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;

/**
 * Jenkins slave {@link NodeProperty} that, when set, causes
 * {@link PrePostClean} to skip the {@link Node}.
 */
public class DisablePrePostCleanNodeProperty extends NodeProperty<Node> {

    @DataBoundConstructor
    public DisablePrePostCleanNodeProperty() {
    }

    @Extension
    public static final class NodePropertyDescriptorImpl extends NodePropertyDescriptor {

        public NodePropertyDescriptorImpl() {
            super(DisablePrePostCleanNodeProperty.class);
        }

        @Override
        public String getDisplayName() {
            return Messages.DisablePrePostCleanNodeProperty_displayName();
        }
    }
}
