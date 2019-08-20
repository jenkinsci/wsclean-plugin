package de.jamba.hudson.plugin.wsclean;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.Nonnull;

import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;

@Extension
public class CommonConfig extends GlobalConfiguration {
    /** @return the singleton instance */
    public static CommonConfig get() {
        return GlobalConfiguration.all().get(CommonConfig.class);
    }

    private static final char LINE_SEPARATOR = '\n';
    private static final Joiner LINE_JOINER = Joiner.on(LINE_SEPARATOR).skipNulls();
    private static final Splitter LINE_SPLITTER = Splitter.on(LINE_SEPARATOR).omitEmptyStrings();

    public static enum NodeSelection {
        LABEL_ONLY(true, false, Messages._NodeSelection_LABEL_ONLY_displayName()), // default
        HISTORY_ONLY(false, true, Messages._NodeSelection_HISTORY_ONLY_displayName()),
        LABEL_AND_HISTORY(true, true, Messages._NodeSelection_LABEL_AND_HISTORY_displayName());
        private final boolean labels;
        private final boolean history;
        private final Localizable description;

        private NodeSelection(boolean labels, boolean history, Localizable description) {
            this.labels = labels;
            this.history = history;
            this.description = description;
        }

        public String getDescription() {
            return description.toString();
        }

        public boolean getUseLabels() {
            return labels;
        }

        public boolean getUseHistory() {
            return history;
        }
    }

    private static final NodeSelection DEFAULT_NODESELECTION = NodeSelection.LABEL_ONLY;
    private static final boolean DEFAULT_SKIPROAMING = true; // legacy default
    private static final boolean DEFAULT_PARALLEL = true;
    private static final String[] DEFAULT_NODENAMESTOSKIP = new String[0];
    private static final long DEFAULT_TIMEOUTINMILLISECONDS = 15L * 60L * 1000L; // 15 minutes
    private NodeSelection nodeSelection = null; // our getter will return the default
    private boolean skipRoaming = DEFAULT_SKIPROAMING;
    private boolean parallel = DEFAULT_PARALLEL;
    private String[] nodeNamesToSkip = DEFAULT_NODENAMESTOSKIP;
    private transient Pattern[] nodeNamesToSkipPatterns;
    private long timeoutInMilliseconds = DEFAULT_TIMEOUTINMILLISECONDS;

    public CommonConfig() {
        // When Jenkins is restarted, load any saved configuration from disk.
        load();
    }

    public @Nonnull NodeSelection getNodeSelection() {
        return nodeSelection == null ? DEFAULT_NODESELECTION : nodeSelection;
    }

    @DataBoundSetter
    public void setNodeSelection(NodeSelection nodeSelection) {
        this.nodeSelection = nodeSelection;
        save();
    }

    public boolean getSkipRoaming() {
        return skipRoaming;
    }

    @DataBoundSetter
    public void setSkipRoaming(boolean skipRoaming) {
        this.skipRoaming = skipRoaming;
        save();
    }

    public boolean getParallel() {
        return parallel;
    }

    @DataBoundSetter
    public void setParallel(boolean parallel) {
        this.parallel = parallel;
        save();
    }

    public @Nonnull String[] getNodeNamesToSkip() {
        return nodeNamesToSkip == null ? new String[0] : Arrays.copyOf(nodeNamesToSkip, nodeNamesToSkip.length);
    }

    public String getNodeNamesToSkipString() {
        return LINE_JOINER.join(getNodeNamesToSkip());
    }

    /**
     * Gets {@link #getNodeNamesToSkip()} as a series of {@link Pattern}s. Any
     * values of {@link #getNodeNamesToSkip()} that aren't valid will be silently
     * omitted.
     * 
     * @return An array of {@link Pattern}s. This will not be null.
     */
    @Restricted(NoExternalUse.class)
    Pattern[] getNodeNamesToSkipPatterns() {
        return nodeNamesToSkipPatterns == null ? new Pattern[0] : nodeNamesToSkipPatterns;
    }

    @DataBoundSetter
    public void setNodeNamesToSkip(String[] nodeNamesToSkip) {
        final List<Pattern> patterns;
        if (nodeNamesToSkip == null) {
            patterns = Lists.newArrayList();
            this.nodeNamesToSkip = new String[0];
        } else {
            final int length = nodeNamesToSkip.length;
            patterns = Lists.newArrayListWithCapacity(length);
            for (final String nodeNameToSkip : nodeNamesToSkip) {
                try {
                    final Pattern p = Pattern.compile(nodeNameToSkip);
                    patterns.add(p);
                } catch (PatternSyntaxException ex) {
                    // ignore and skip it
                }
            }
            this.nodeNamesToSkip = Arrays.copyOf(nodeNamesToSkip, length);
        }
        this.nodeNamesToSkipPatterns = patterns.toArray(new Pattern[patterns.size()]);
        save();
    }

    @DataBoundSetter
    public void setNodeNamesToSkipString(String nodeNamesToSkipString) {
        setNodeNamesToSkip(splitAndFilterEmpty(nodeNamesToSkipString));
    }

    public long getTimeoutInMilliseconds() {
        return timeoutInMilliseconds < 0L ? 0L : timeoutInMilliseconds;
    }

    @DataBoundSetter
    public void setTimeoutInMilliseconds(long timeoutInMilliseconds) {
        this.timeoutInMilliseconds = timeoutInMilliseconds;
    }

    public FormValidation doCheckSkipRoaming(@QueryParameter boolean value, @QueryParameter String nodeSelection) {
        NodeSelection nodeSelectionEnum;
        try {
            nodeSelectionEnum = NodeSelection.valueOf(nodeSelection);
        } catch (IllegalArgumentException ex) {
            nodeSelectionEnum = null;
        }
        if (NodeSelection.HISTORY_ONLY.equals(nodeSelectionEnum)) {
            return FormValidation.warning(Messages.CommonConfig_skipRoamingIgnores());
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckNodeNamesToSkipString(@QueryParameter String value) {
        final String[] values = splitAndFilterEmpty(value);
        final List<String> warnings = Lists.newArrayList();
        final List<String> errors = Lists.newArrayList();
        for (int i = 0; i < values.length; i++) {
            final int number = i + 1;
            final String thisValue = values[i];
            if (thisValue.startsWith(" ")) {
                warnings.add(Messages.CommonConfig_nodeNamesToSkip_whitespaceFirst(number));
            }
            if (thisValue.endsWith(" ")) {
                warnings.add(Messages.CommonConfig_nodeNamesToSkip_whitespaceLast(number));
            }
            try {
                Pattern.compile(thisValue);
            } catch (PatternSyntaxException ex) {
                errors.add(Messages.CommonConfig_nodeNamesToSkip_invalid(number, ex.getMessage()));
            }
        }
        if (!errors.isEmpty()) {
            return FormValidation.error(errors.get(0));
        }
        if (!warnings.isEmpty()) {
            return FormValidation.warning(warnings.get(0));
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckTimeoutInMilliseconds(@QueryParameter String value) {
        return FormValidation.validateNonNegativeInteger(value);
    }

    private static String[] splitAndFilterEmpty(String s) {
        final List<String> result = Lists.newArrayList();
        if (s != null) {
            for (final String o : LINE_SPLITTER.split(s)) {
                result.add(o);
            }
        }
        return result.toArray(new String[result.size()]);
    }
}
