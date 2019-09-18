package de.jamba.hudson.plugin.wsclean;

import static de.jamba.hudson.plugin.wsclean.TaskUtils.runWithTimeout;
import static de.jamba.hudson.plugin.wsclean.TaskUtils.runWithoutTimeout;
import static de.jamba.hudson.plugin.wsclean.TaskUtils.waitUntilAllAreDone;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;

import de.jamba.hudson.plugin.wsclean.CommonConfig.NodeSelection;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TopLevelItem;
import hudson.remoting.RequestAbortedException;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.RunList;
import jenkins.model.Jenkins;

public class PrePostClean extends BuildWrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrePostClean.class);

    private boolean before;

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

    /**
     * If set, we clean at the start of the build instead of at the end of the
     * build.
     * 
     * @return true if we run before the build.
     */
    public boolean isBefore() {
        return before;
    }

    @DataBoundSetter
    public void setBefore(boolean before) {
        this.before = before;
    }

    // Main entry point to our functionality.
    // This gets called when the build starts, and returns a hook that's run when
    // the build finishes, allowing our code to get called.
    @SuppressWarnings("rawtypes")
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        final boolean runAtStart = isBefore();
        final boolean runAtEnd = !runAtStart;
        final CommonConfig commonConfig = CommonConfig.get();
        final boolean skipRoaming = commonConfig.getSkipRoaming();
        final NodeSelection nodeSelectionMethod = commonConfig.getNodeSelection();
        final Pattern[] nodeNamesToSkip = commonConfig.getNodeNamesToSkipPatterns();
        final boolean parallel = commonConfig.getParallel();
        final long timeoutInMs = commonConfig.getTimeoutInMilliseconds();
        final Jenkins jenkins = Jenkins.getInstance();
        final ExecutorService parallelExecutor = Computer.threadPoolForRemoting;
        LOGGER.info(
                "setUp({},,): runAtStart={}, runAtEnd={}, nodeSelectionMethod={}, skipRoaming={}, nodeNamesToSkip={}, parallel={}, timeoutInMs={}",
                build, runAtStart, runAtEnd, nodeSelectionMethod.name(), skipRoaming, Arrays.asList(nodeNamesToSkip),
                parallel, timeoutInMs);
        // TearDown
        class TearDownImpl extends Environment {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                if (runAtEnd) {
                    executeOnSlaves("Post", jenkins, parallelExecutor, build, listener, nodeSelectionMethod,
                            skipRoaming, nodeNamesToSkip, parallel, timeoutInMs);
                }
                return super.tearDown(build, listener);
            }
        }

        if (runAtStart) {
            executeOnSlaves("Pre", jenkins, parallelExecutor, build, listener, nodeSelectionMethod, skipRoaming,
                    nodeNamesToSkip, parallel, timeoutInMs);
        }
        return new TearDownImpl();
    }

    /**
     * Does the clean-up, and says so.
     * 
     * @param preOrPost       String "Pre" or "Post". Only affects logging output.
     * @param build           The build this action is part of.
     * @param listener        The build output log we can append to.
     * @param jenkins         Maps node names to nodes.
     * @param executor        Means of running multiple threads in parallel.
     * @param nodeSelection   Method we're going to use to decide what to clean.
     * @param nodeNamesToSkip List of regexes matching node names to skip.
     * @param parallel        If true we do the deletion in parallel, if false we do
     *                        each node in sequence.
     * @param timeoutInMs     If >0, timeout for the deletion in milliseconds.
     * @throws InterruptedException if we are interrupted before we are complete.
     */
    @Restricted(NoExternalUse.class) // package-level to avoid accessor class
    void executeOnSlaves(String preOrPost, Jenkins jenkins, ExecutorService executor, AbstractBuild<?, ?> build,
            BuildListener listener, NodeSelection nodeSelection, boolean skipRoaming, Pattern[] nodeNamesToSkip,
            boolean parallel, long timeoutInMs) throws InterruptedException {
        listener.getLogger().println(preOrPost + "-build clean running...");
        String result = "abandoned";
        try {
            final boolean success = cleanUp(jenkins, executor, build, listener, nodeSelection, skipRoaming,
                    nodeNamesToSkip, parallel, timeoutInMs);
            result = success ? "completed" : "failed";
        } finally {
            listener.getLogger().println(preOrPost + "-build clean " + result + ".");
        }
    }

    private boolean cleanUp(Jenkins jenkins, ExecutorService executor, AbstractBuild<?, ?> build,
            BuildListener listener, NodeSelection nodeSelection, boolean skipRoaming, Pattern[] nodeNameRegexsToSkip,
            boolean parallel, long timeoutInMs) throws InterruptedException {
        LOGGER.debug("cleanUp({}) started", build);
        final Multimap<String, String> workspacesToBeRemoved = calculateWssForRemoval(jenkins, build, listener,
                nodeSelection, skipRoaming);
        LOGGER.debug("cleanUp({}): calculateWssForRemoval(,,,{},{})={}", build, nodeSelection, skipRoaming,
                workspacesToBeRemoved);
        final List<String> nodesToSkipDueToTheirName = getMatching(workspacesToBeRemoved.keySet(),
                nodeNameRegexsToSkip);
        LOGGER.debug("cleanUp({}): nodesToSkipDueToTheirName={}", build, nodesToSkipDueToTheirName);
        workspacesToBeRemoved.keySet().removeAll(nodesToSkipDueToTheirName);
        final List<String> nodesToSkipDueToNodeProperty = getNodesWithDisableProperty(workspacesToBeRemoved.keySet(),
                jenkins);
        LOGGER.debug("cleanUp({}): nodesToSkipDueToNodeProperty={}", build, nodesToSkipDueToNodeProperty);
        workspacesToBeRemoved.keySet().removeAll(nodesToSkipDueToNodeProperty);
        class CleanOldWorkspaces implements Callable<Void> {
            @Override
            public Void call() throws InterruptedException {
                if (parallel) {
                    LOGGER.debug("cleanUp({}): deleteWssInParallel...", build);
                    deleteWssInParallel(build, jenkins, executor, workspacesToBeRemoved, listener);
                } else {
                    LOGGER.debug("cleanUp({}): deleteWssInSeries...", build);
                    deleteWssInSeries(build, jenkins, workspacesToBeRemoved, listener);
                }
                LOGGER.debug("cleanUp({}): deleted.", build);
                return null;
            }
        }
        final Callable<Void> deletionTask = new CleanOldWorkspaces();
        boolean success = false;
        if (timeoutInMs > 0L) {
            LOGGER.debug("cleanUp({}): using timeout of {}.", build, timeoutInMs);
            try {
                runWithTimeout(executor, timeoutInMs, deletionTask);
                success = true;
            } catch (TimeoutException e) {
                listener.getLogger().println("Clean did not complete within " + timeoutInMs + " milliseconds.");
            }
        } else {
            runWithoutTimeout(deletionTask);
            success = true;
        }
        LOGGER.debug("cleanUp({}): completed.", build);
        return success;
    }

    /**
     * Calculates what workspaces we should consider for removal, taking
     * <em>everything</em> into consideration.
     * 
     * @param jenkins       Contains all our possible nodes.
     * @param build         Our current build.
     * @param listener      User-facing log where we can log progress reports to the
     *                      build.
     * @param nodeSelection Says how we'll decide.
     * @param skipRoaming   If we should ignore "nodes matching label expression" if
     *                      we have no label expression.
     * @return A map of node names to lists of workspace locations.
     */
    private Multimap<String, String> calculateWssForRemoval(Jenkins jenkins, AbstractBuild<?, ?> build,
            BuildListener listener, NodeSelection nodeSelection, boolean skipRoaming) {
        final Multimap<String, String> wssForRemovalFromLabels;
        if (nodeSelection.getUseLabels()) {
            wssForRemovalFromLabels = TreeMultimap.create();
            findPossibleWssFromJobLabel(wssForRemovalFromLabels, jenkins, build, listener, skipRoaming);
        } else {
            wssForRemovalFromLabels = null;
        }
        final Multimap<String, String> oldWssFromHistory;
        final Multimap<String, String> currentWssFromHistory;
        final Set<String> nodeNamesOfDeadNodes;
        if (nodeSelection.getUseHistory() || build.getProject().isConcurrentBuild()) {
            oldWssFromHistory = TreeMultimap.create();
            currentWssFromHistory = TreeMultimap.create();
            nodeNamesOfDeadNodes = Sets.newTreeSet();
            findWssFromBuildHistory(currentWssFromHistory, oldWssFromHistory, nodeNamesOfDeadNodes, build);
        } else {
            oldWssFromHistory = null;
            currentWssFromHistory = null;
            nodeNamesOfDeadNodes = null;
        }
        // Now work out what locations are safe to remove
        final Multimap<String, String> workspacesToBeRemoved = TreeMultimap.create();
        // Include stuff from labels if we want to
        if (nodeSelection.getUseLabels()) {
            workspacesToBeRemoved.putAll(wssForRemovalFromLabels);
        }
        // Include stuff from history if we want to
        if (nodeSelection.getUseHistory()) {
            workspacesToBeRemoved.putAll(oldWssFromHistory);
            for (final String offlineNode : nodeNamesOfDeadNodes) {
                workspacesToBeRemoved.removeAll(offlineNode);
            }
        }
        // Exclude currently-running builds if we know them
        if (currentWssFromHistory != null) {
            // We looked for all currently-running builds, which includes us.
            for (final Map.Entry<String, String> workspaceCurrentlyInUse : currentWssFromHistory.entries()) {
                workspacesToBeRemoved.remove(workspaceCurrentlyInUse.getKey(), workspaceCurrentlyInUse.getValue());
            }
        }
        return workspacesToBeRemoved;
    }

    /**
     * Uses the job label expression to determine what slave nodes this build could
     * run on and, from that, guess what workspaces we should delete. Note: This
     * does not take concurrent builds into account.
     * 
     * @param result      Where to put the workspaces we identify.
     * @param jenkins     Used to determine all possible nodes if we are a roaming
     *                    build and skipRoaming is false.
     * @param build       Our current build.
     * @param listener    User-facing log where we can log progress reports to the
     *                    build.
     * @param skipRoaming If we should return nothing (instead of everything) if we
     *                    have no label expression.
     */
    private void findPossibleWssFromJobLabel(final Multimap<String, String> result, Jenkins jenkins,
            AbstractBuild<?, ?> build, BuildListener listener, boolean skipRoaming) {
        // select actual running label
        String runNode = build.getBuiltOnStr();
        AbstractProject<?, ?> project = build.getProject();
        Label assignedLabel = project.getAssignedLabel();
        if (assignedLabel == null && skipRoaming) {
            listener.getLogger().println("Skipping roaming project.");
            return;
        }
        Set<Node> nodesForLabel = assignedLabel != null ? assignedLabel.getNodes() : getAllNonexclusiveNodes(jenkins);
        LOGGER.debug("calculatePotentialWssFromJobLabel(,{},{}): assignedLabel={} evaluates to nodesForLabel={}", build,
                skipRoaming, assignedLabel == null ? null : assignedLabel.getExpression(), nodesForLabel);
        if (nodesForLabel != null) {
            for (Node node : nodesForLabel) {
                String nodeName = node.getNodeName();
                if (!runNode.equals(nodeName)) {
                    String normalizedName = toNormalizedNodeName(nodeName);
                    String folderOnNode = getWorkspaceOn(project, listener, node, normalizedName);
                    LOGGER.debug("calculatePotentialWssFromJobLabel(,{},{}): Node={}, folder={}", build, skipRoaming,
                            nodeName, folderOnNode);
                    if (folderOnNode != null) {
                        result.put(nodeName, folderOnNode);
                    }
                } else {
                    LOGGER.debug("calculatePotentialWssFromJobLabel(,{},{}): Node={} is current node, so excluding",
                            build, skipRoaming, nodeName);
                }
            }
        }
    }

    /**
     * Uses the old build history to determine what workspaces (on what slave nodes)
     * we should delete.
     * 
     * @param wssCurrentlyInUse    Where to record workspaces that are currently in
     *                             use.
     * @param wssPreviouslyUsed    Where to record workspaces that are no longer in
     *                             use.
     * @param nodeNamesOfDeadNodes Where to record nodes which aren't online so we
     *                             need to avoid touching them at all.
     * @param build                The build we're doing this on.
     */
    private void findWssFromBuildHistory(Multimap<String, String> wssCurrentlyInUse,
            Multimap<String, String> wssPreviouslyUsed, Set<String> nodeNamesOfDeadNodes, AbstractBuild<?, ?> build) {
        final AbstractProject<?, ?> project = build.getProject();
        // First, figure out the overall build history
        final RunList<?> builds = project.getBuilds();
        for (final Object historyEntry : builds) {
            if (!(historyEntry instanceof AbstractBuild)) {
                LOGGER.debug("calculateUnusedWssFromBuildHistory({}): {} is not AbstractBuild", build, historyEntry);
                continue;
            }
            final AbstractBuild<?, ?> historicalBuild = (AbstractBuild<?, ?>) historyEntry;
            if (historicalBuild.hasntStartedYet()) {
                LOGGER.debug("calculateUnusedWssFromBuildHistory({}): {} has not started", build, historicalBuild);
                continue; // no node or ws assigned yet
            }
            final String nodeItRanOn = Util.fixNull(historicalBuild.getBuiltOnStr());
            final Node node = historicalBuild.getBuiltOn();
            if (node == null) {
                // Node no longer exists
                LOGGER.debug("calculateUnusedWssFromBuildHistory({}): {} ran on node {} which is deleted.", build,
                        historicalBuild, nodeItRanOn);
                nodeNamesOfDeadNodes.add(nodeItRanOn);
                continue;
            }
            final FilePath wsOrNull = historicalBuild.getWorkspace();
            if (wsOrNull == null) {
                // Node is offline
                LOGGER.debug(
                        "calculateUnusedWssFromBuildHistory({}): {} ran on node {} which is offline so ws unavailable.",
                        build, historicalBuild, nodeItRanOn);
                nodeNamesOfDeadNodes.add(nodeItRanOn);
                continue;
            }
            final boolean buildIsNotFinished = historicalBuild.isBuilding() || historicalBuild.getExecutor() != null;
            final String folderOnNode = wsOrNull.getRemote();
            LOGGER.debug("calculateUnusedWssFromBuildHistory({}): Unfinished={} {} ran on node {} in folder {}.", build,
                    buildIsNotFinished, historicalBuild, nodeItRanOn, folderOnNode);
            if (buildIsNotFinished) {
                wssCurrentlyInUse.put(nodeItRanOn, folderOnNode);
            } else {
                wssPreviouslyUsed.put(nodeItRanOn, folderOnNode);
            }
        }
    }

    /**
     * Deletes workspaces, one workspace at a time, all in this thread.
     * 
     * @param build                 The build this is for. This is only used for
     *                              diagnostic logging.
     * @param nodeContainer         The Jenkins node that can turn node names into
     *                              Nodes.
     * @param workspacesToBeRemoved The set of workspaces to be removed.
     * @param listener              User-facing log where we can log progress
     *                              reports to the build.
     * @throws InterruptedException if we are interrupted (e.g. if the build is
     *                              cancelled).
     */
    @Restricted(NoExternalUse.class)
    void deleteWssInSeries(AbstractBuild<?, ?> build, Jenkins nodeContainer,
            Multimap<String, String> workspacesToBeRemoved, BuildListener listener) throws InterruptedException {
        for (final Map.Entry<String, ? extends Iterable<String>> e : workspacesToBeRemoved.asMap().entrySet()) {
            final Iterable<String> foldersToDelete = e.getValue();
            final String nodeName = e.getKey();
            final String normalizedNodeName = toNormalizedNodeName(nodeName);
            final Node node = getNode(nodeContainer, nodeName);
            if (node == null) {
                LOGGER.debug("deleteWssInSeries({}): node==null for normalizedNodeName={}, foldersToDelete={}", build,
                        normalizedNodeName, foldersToDelete);
                continue; // it's gone while we were mid-calculation
            }
            for (final String folderToDelete : foldersToDelete) {
                final FilePath fp = node.createPath(folderToDelete);
                if (fp == null) {
                    LOGGER.debug("deleteWssInSeries({}): fp==null for normalizedNodeName={}, folderToDelete={}", build,
                            normalizedNodeName, folderToDelete);
                    continue; // it's gone offline while we were mid-calculation
                }
                listener.getLogger().println("Cleaning " + normalizedNodeName + " folder " + fp);
                LOGGER.debug("deleteWssInSeries({}): deleting normalizedNodeName={}, folderToDelete={}", build,
                        normalizedNodeName, folderToDelete);
                deleteWorkspaceOn(build, listener, normalizedNodeName, fp);
            }
        }
    }

    /**
     * Deletes workspaces, using a separate thread for each slave node affected so
     * that all the slave nodes do their deletions in parallel.
     * 
     * @param build                 The build this is for. This is only used for
     *                              diagnostic logging.
     * @param nodeContainer         The Jenkins node that can turn node names into
     *                              Nodes.
     * @param parallelExecutor      Thread provider that'll be running the deletions
     *                              for us.
     * @param workspacesToBeRemoved The set of workspaces to be removed.
     * @param listener              User-facing log where we can log progress
     *                              reports to the build.
     * @throws InterruptedException if we are interrupted (e.g. if the build is
     *                              cancelled).
     */
    @Restricted(NoExternalUse.class)
    void deleteWssInParallel(AbstractBuild<?, ?> build, Jenkins nodeContainer, ExecutorService parallelExecutor,
            Multimap<String, String> workspacesToBeRemoved, BuildListener listener) throws InterruptedException {
        final List<Future<?>> deletionTaskResults = Lists.newArrayList();
        for (final Map.Entry<String, ? extends Iterable<String>> e : workspacesToBeRemoved.asMap().entrySet()) {
            final Iterable<String> foldersToDelete = e.getValue();
            final String nodeName = e.getKey();
            final String normalizedNodeName = toNormalizedNodeName(nodeName);
            final Node node = getNode(nodeContainer, nodeName);
            if (node == null) {
                LOGGER.debug("deleteWssInParallel({}): node==null for normalizedNodeName={}, foldersToDelete={}", build,
                        normalizedNodeName, foldersToDelete);
                continue; // it's gone while we were mid-calculation
            }
            class CleanFoldersOnOneNode implements Callable<Void> {
                @Override
                public Void call() throws Exception {
                    try {
                        for (final String folderToDelete : foldersToDelete) {
                            final FilePath fp = node.createPath(folderToDelete);
                            if (fp == null) {
                                continue; // it's gone offline while we were mid-calculation
                            }
                            listener.getLogger().println("Cleaning " + normalizedNodeName + " folder " + fp);
                            deleteWorkspaceOn(build, listener, normalizedNodeName, fp);
                        }
                    } catch (InterruptedException e) {
                        listener.getLogger().println("Cleaning on " + normalizedNodeName + " was interrupted.");
                    }
                    return null;
                }
            }
            final Callable<Void> task = new CleanFoldersOnOneNode();
            LOGGER.debug("deleteWssInParallel({}): submitting task to delete normalizedNodeName={}, foldersToDelete={}",
                    build, normalizedNodeName, foldersToDelete);
            final Future<?> futureResult = parallelExecutor.submit(task);
            deletionTaskResults.add(futureResult);
        }
        LOGGER.debug("deleteWssInParallel({}): waiting for {} deletions to complete", build,
                deletionTaskResults.size());
        try {
            waitUntilAllAreDone(deletionTaskResults);
            LOGGER.debug("deleteWssInParallel({}): wait complete", build);
        } catch (InterruptedException ex) {
            // if we're interrupted, we tell all our other tasks to abort.
            for (Future<?> t : deletionTaskResults) {
                t.cancel(true);
            }
            throw ex;
        }
    }

    private String getWorkspaceOn(AbstractProject<?, ?> project, BuildListener listener, Node node, String nodeName) {
        if (project instanceof TopLevelItem) {
            FilePath fp = node.getWorkspaceFor((TopLevelItem) project);
            if (fp != null) {
                return fp.getRemote();
            } else {
                listener.getLogger().println("No workspace found on " + nodeName + ". Node is maybe offline.");
            }
        } else {
            listener.getLogger().println("Project is not TopLevelItem! Cannot determine other workspaces!");
        }
        return null;
    }

    /**
     * This is only non-private for test purposes. Wipes the workspace at the given
     * location, logging any problems.
     * 
     * @param build    The build this is for (used for logging only).
     * @param listener Where to log progress/issues.
     * @param nodeName Human-friendly name of the node we're working on (used for
     *                 logging only).
     * @param fp       The workspace to be wiped.
     * @throws InterruptedException if we are interrupted.
     */
    @Restricted(NoExternalUse.class) // unit-test only
    void deleteWorkspaceOn(AbstractBuild<?, ?> build, BuildListener listener, String nodeName, FilePath fp)
            throws InterruptedException {
        try {
            LOGGER.trace("deleteWorkspaceOn({}): Deleting {} on node {}", build, fp.getRemote(), nodeName);
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

    private static Node getNode(Jenkins nodeContainer, String nodeName) {
        if (nodeName.isEmpty()) {
            return nodeContainer;
        }
        return nodeContainer.getNode(nodeName);
    }

    private static List<String> getMatching(Iterable<String> input, Pattern[] patternsToMatch) {
        final List<String> result = Lists.newArrayList();
        for (final String s : input) {
            for (final Pattern p : patternsToMatch) {
                if (p.matcher(s).matches()) {
                    result.add(s);
                    break; // go to next input string
                }
            }
        }
        return result;
    }

    private static Set<Node> getAllNonexclusiveNodes(Jenkins jenkins) {
        final Set<Node> result = Sets.newHashSet();
        for (final Node n : jenkins.getNodes()) {
            if (Node.Mode.NORMAL.equals(n.getMode())) {
                result.add(n);
            }
        }
        if (Node.Mode.NORMAL.equals(jenkins.getMode())) {
            result.add(jenkins);
        }
        return result;
    }

    private static List<String> getNodesWithDisableProperty(Set<String> nodesToConsider, Jenkins jenkins) {
        final List<String> result = Lists.newArrayList();
        for (final Node n : jenkins.getNodes()) {
            final String nodeName = n.getNodeName();
            if (nodesToConsider.contains(nodeName)
                    && n.getNodeProperty(DisablePrePostCleanNodeProperty.class) != null) {
                result.add(nodeName);
            }
        }
        final String nodeName = "";
        if (nodesToConsider.contains(nodeName)
                && jenkins.getNodeProperty(DisablePrePostCleanNodeProperty.class) != null) {
            result.add(nodeName);
        }
        return result;
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
