package de.jamba.hudson.plugin.wsclean;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.AfterClass;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

import de.jamba.hudson.plugin.wsclean.CommonConfig.NodeSelection;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Executor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TopLevelItem;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildWrapper.Environment;
import hudson.util.RunList;
import jenkins.model.Jenkins;
import jenkins.model.TestJenkins;

@SuppressWarnings("rawtypes")
public class PrePostCleanTest {

    @AfterClass
    public static void tearDownClass() {
        TestJenkins.setJenkinsInstance(null);
    }

    @Test
    public void constructorGivenDefaultsThenReturnsDefaultInstance() throws Exception {
        // Given
        final boolean expectedBefore = false;

        // When
        final PrePostClean instance = new PrePostClean();
        final boolean actualBefore = instance.isBefore();

        // Then
        assertThat(actualBefore, equalTo(expectedBefore));
    }

    @Test
    public void setUpGivenBeforeIsFalseThenDoesNothingBefore() throws Exception {
        // Given
        final PrePostClean instance = new PrePostClean(false);
        final AbstractBuild mockBuild = mock(AbstractBuild.class);
        final Launcher mockLauncher = mock(Launcher.class);
        final BuildListener mockListener = mock(BuildListener.class);

        // When
        instance.setUp(mockBuild, mockLauncher, mockListener);

        // Then
        verifyNoMoreInteractions(mockBuild, mockLauncher, mockListener);
    }

    @Test
    public void setUpGivenBeforeIsFalseAndNotRoamingThenCleansOnlineMatchingNodesDuringTeardown() throws Exception {
        // Given
        final AbstractBuild mockBuild = mock(AbstractBuild.class, "mockBuild");
        final Launcher mockLauncher = mock(Launcher.class);
        final BuildListener mockListener = mock(BuildListener.class, "mockListener");
        when(mockListener.getLogger()).thenReturn(System.out);
        final AbstractProject mockProject = mock(AbstractProject.class,
                withSettings().name("mockProject").extraInterfaces(TopLevelItem.class));
        final Label mockAssignedLabel = mock(Label.class, "mockAssignedLabel");
        final String node1Name = "nodeA1-current";
        final String node2Name = "nodeA2";
        final String node3Name = "nodeA3";
        final String node4Name = "nodeA4-offline";
        final Jenkins mockJenkins = mockNode(Jenkins.class, "mockJenkins", "", true);
        final Node mockNode1 = mockNode("mockNode1", node1Name, true);
        final Node mockNode2 = mockNode("mockNode2", node2Name, true);
        final Node mockNode3 = mockNode("mockNode3", node3Name, true);
        final Node mockNode4 = mockNode("mockNode4", node4Name, false);
        final Set<Node> setOfMockNodes = ImmutableSet.of(mockNode1, mockNode2, mockNode3, mockNode4);
        final String ws = "/workspaces/myBuild";
        final FilePath node1ws = mockNode1.createPath(ws);
        final FilePath node2ws = mockNode2.createPath(ws);
        final FilePath node3ws = mockNode3.createPath(ws);
        when(mockNode1.getWorkspaceFor((TopLevelItem) mockProject)).thenReturn(node1ws);
        when(mockNode2.getWorkspaceFor((TopLevelItem) mockProject)).thenReturn(node2ws);
        when(mockNode3.getWorkspaceFor((TopLevelItem) mockProject)).thenReturn(node3ws);
        when(mockNode4.getWorkspaceFor((TopLevelItem) mockProject)).thenReturn(null); // we're offline
        when(mockBuild.getBuiltOnStr()).thenReturn(node1Name);
        when(mockBuild.getProject()).thenReturn(mockProject);
        when(mockProject.getAssignedLabel()).thenReturn(mockAssignedLabel);
        when(mockAssignedLabel.getNodes()).thenReturn(setOfMockNodes);
        TestJenkins.setJenkinsInstance(mockJenkins);
        whenJenkinsGetNode(mockJenkins, mockNode1, mockNode2, mockNode3, mockNode4);
        final TestPrePostClean instance = new TestPrePostClean();
        instance.setBefore(false);
        CommonConfigTest.stubConfig(mockJenkins, NodeSelection.LABEL_ONLY, true, false, null, 0);
        final Environment env = instance.setUp(mockBuild, mockLauncher, mockListener);
        verifyNoMoreInteractions(mockBuild, mockLauncher, mockListener, instance.mock);

        // When
        env.tearDown(mockBuild, mockListener);

        // Then
        final InOrder inOrder = inOrder(instance.mock);
        // not expecting node1 to be cleaned as that's the current node
        inOrder.verify(instance.mock).deleteWorkspaceOn(mockListener, node2Name, node2ws);
        inOrder.verify(instance.mock).deleteWorkspaceOn(mockListener, node3Name, node3ws);
        // not expecting node4 to be cleaned as it's offline
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void setUpGivenBeforeIsTrueAndNotRoamingThenCleansOnlineMatchingNodesBeforeAndNotAfter() throws Exception {
        // Given
        final AbstractBuild mockBuild = mock(AbstractBuild.class, "mockBuild");
        final Launcher mockLauncher = mock(Launcher.class);
        final BuildListener mockListener = mock(BuildListener.class, "mockListener");
        when(mockListener.getLogger()).thenReturn(System.out);
        final AbstractProject mockProject = mock(AbstractProject.class,
                withSettings().name("mockProject").extraInterfaces(TopLevelItem.class));
        final Label mockAssignedLabel = mock(Label.class, "mockAssignedLabel");
        final String node1Name = "nodeA1-current";
        final String node2Name = "nodeA2";
        final String node3Name = "nodeA3";
        final String node4Name = "nodeA4-offline";
        final String node5Name = "nodeA5-offline";
        final Jenkins mockJenkins = mockNode(Jenkins.class, "mockJenkins", "", true);
        final Node mockNode1 = mockNode("mockNode1", node1Name, true);
        final Node mockNode2 = mockNode("mockNode2", node2Name, true);
        final Node mockNode3 = mockNode("mockNode3", node3Name, true);
        final Node mockNode4 = mockNode("mockNode4", node4Name, false);
        final Node mockNode5 = mockNode("mockNode5", node5Name, false);
        final Set<Node> setOfMockNodes = ImmutableSet.of(mockNode1, mockNode2, mockNode3, mockNode4, mockNode5);
        final String ws = "/workspaces/myBuild";
        final FilePath node1ws = mockNode1.createPath(ws);
        final FilePath node2ws = mockNode2.createPath(ws);
        final FilePath node3ws = mockNode3.createPath(ws);
        when(mockNode1.getWorkspaceFor((TopLevelItem) mockProject)).thenReturn(node1ws);
        when(mockNode2.getWorkspaceFor((TopLevelItem) mockProject)).thenReturn(node2ws);
        when(mockNode3.getWorkspaceFor((TopLevelItem) mockProject)).thenReturn(node3ws);
        when(mockNode4.getWorkspaceFor((TopLevelItem) mockProject)).thenReturn(null); // we're offline
        when(mockNode5.getWorkspaceFor((TopLevelItem) mockProject)).thenReturn(null); // we're offline
        when(mockBuild.getBuiltOnStr()).thenReturn(node1Name);
        when(mockBuild.getProject()).thenReturn(mockProject);
        when(mockProject.getAssignedLabel()).thenReturn(mockAssignedLabel);
        when(mockAssignedLabel.getNodes()).thenReturn(setOfMockNodes);
        TestJenkins.setJenkinsInstance(mockJenkins);
        whenJenkinsGetNode(mockJenkins, mockNode1, mockNode2, mockNode3, mockNode4, mockNode5);
        final TestPrePostClean instance = new TestPrePostClean();
        instance.setBefore(true);
        CommonConfigTest.stubConfig(mockJenkins, NodeSelection.LABEL_ONLY, true, false, null, 10000L);

        // When
        final Environment env = instance.setUp(mockBuild, mockLauncher, mockListener);

        // Then
        final InOrder inOrder = inOrder(instance.mock);
        // not expecting node1 to be cleaned as that's the current node
        inOrder.verify(instance.mock).deleteWorkspaceOn(mockListener, node2Name, node2ws);
        inOrder.verify(instance.mock).deleteWorkspaceOn(mockListener, node3Name, node3ws);
        // not expecting node4 to be cleaned as it's offline
        // not expecting node5 to be cleaned as it's offline
        inOrder.verifyNoMoreInteractions();
        env.tearDown(mockBuild, mockListener);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void setUpGivenBeforeIsTrueAndRoamingThenSkips() throws Exception {
        // Given
        final AbstractBuild mockBuild = mock(AbstractBuild.class, "mockBuild");
        final Launcher mockLauncher = mock(Launcher.class);
        final BuildListener mockListener = mock(BuildListener.class, "mockListener");
        when(mockListener.getLogger()).thenReturn(System.out);
        final AbstractProject mockProject = mock(AbstractProject.class,
                withSettings().name("mockProject").extraInterfaces(TopLevelItem.class));
        when(mockBuild.getBuiltOnStr()).thenReturn("someNode");
        when(mockBuild.getProject()).thenReturn(mockProject);
        when(mockProject.getAssignedLabel()).thenReturn(null); // roaming
        final TestPrePostClean instance = new TestPrePostClean();
        instance.setBefore(true);
        final Jenkins mockJenkins = mockNode(Jenkins.class, "mockJenkins", "", true);
        CommonConfigTest.stubConfig(mockJenkins, NodeSelection.LABEL_ONLY, true, false, null, 0L);

        // When
        final Environment env = instance.setUp(mockBuild, mockLauncher, mockListener);

        // Then
        verifyNoMoreInteractions(instance.mock);
        env.tearDown(mockBuild, mockListener);
        verifyNoMoreInteractions(instance.mock);
    }

    @Test
    public void setUpGivenBuildHistoryOnlyAndBuildsThenDeletesOldWorkspaces() throws Exception {
        // Given
        final String normalWs = "/workspacesB/NormalPlace";
        final String weirdWs = "/workspacesB/SomewhereElse";
        final String masterName = "";
        final String normalisedMasterName = "master";
        final String node1Name = "nodeB1";
        final String node2Name = "nodeB2";
        final String node3Name = "nodeB3";
        final String node4Name = "nodeB4";
        final String node5Name = "nodeB5";
        final Jenkins mockMaster = mockNode(Jenkins.class, "mockMaster", masterName, true);
        final Node mockNode1 = mockNode("mockNode1", node1Name, true);
        final Node mockNode2 = mockNode("mockNode2", node2Name, true);
        final Node mockNode3 = mockNode("mockNode3", node3Name, true);
        final Node mockNode4 = mockNode("mockNode4", node4Name, true);
        final Node mockNode5 = mockNode("mockNode5", node5Name, true);
        when(mockNode5.getNodeProperty(DisablePrePostCleanNodeProperty.class))
                .thenReturn(new DisablePrePostCleanNodeProperty());
        final Node mockNodeX = mockNode("mockNodeX", "nodeX", false);
        whenJenkinsGetNode(mockMaster, mockNode1, mockNode2, mockNode3, mockNode4, mockNode5);

        final AbstractBuild mockBuild1 = mockBuild("mockBuild1-completed-ranOnMaster", mockMaster, normalWs, false,
                false);
        final AbstractBuild mockBuild2 = mockBuild("mockBuild2-completed-ranOnNode1", mockNode1, normalWs, false,
                false);
        final AbstractBuild mockBuild3 = mockBuild("mockBuild3-completed-ranOnNode4", mockNode4, normalWs, false,
                false);
        final AbstractBuild mockBuild4 = mockBuild("mockBuild4-completed-ranOnNode3-inDifferentWS", mockNode3, weirdWs,
                false, false);
        final AbstractBuild mockBuild5 = mockBuild("mockBuild5-completed-ranOnNode3", mockNode3, normalWs, false,
                false);
        final AbstractBuild mockBuild6 = mockBuild("mockBuild6-completed-ranOnNode2", mockNode2, normalWs, false,
                false);
        final AbstractBuild mockBuild7 = mockBuild("mockBuild7-completed-ranOnUnknownNodeX", mockNodeX, normalWs, false,
                false);
        final AbstractBuild mockBuild8 = mockBuild("mockBuild8-completed-ranOnDisabledNode", mockNode5, normalWs, false,
                false);
        final AbstractBuild mockBuild9 = mockBuild("mockBuild9-ourCurrentBuild-runningOnNode1", mockNode1, normalWs,
                false, true);
        final AbstractBuild mockBuild10 = mockBuild("mockBuild10-concurrentWithUs-runningOnNode2", mockNode2, normalWs,
                false, true);
        final AbstractBuild mockBuild11 = mockBuild("mockBuild11-allocatedToAgentButNotStartedYet", mockNode2, weirdWs,
                true, false);
        final AbstractBuild mockBuild12 = mockBuild("mockBuild12-notStartedYet", null, normalWs, true, false);
        final List<AbstractBuild> listOfMockBuildHistory = ImmutableList.of(mockBuild12, mockBuild11, mockBuild10,
                mockBuild9, mockBuild8, mockBuild7, mockBuild6, mockBuild5, mockBuild4, mockBuild3, mockBuild2,
                mockBuild1);
        final FilePath masterNormalWs = mockMaster.createPath(normalWs);
        final FilePath node3NormalWs = mockNode3.createPath(normalWs);
        final FilePath node3WeirdWs = mockNode3.createPath(weirdWs);
        final FilePath node4NormalWs = mockNode4.createPath(normalWs);

        final AbstractBuild mockCurrentBuild = mockBuild7;
        final AbstractProject mockProject = mock(AbstractProject.class, withSettings().name("mockProject"));
        when(mockCurrentBuild.getProject()).thenReturn(mockProject);
        when(mockProject.getBuilds()).thenReturn(RunList.fromRuns(listOfMockBuildHistory));
        final BuildListener mockListener = mock(BuildListener.class, "mockListener");
        when(mockListener.getLogger()).thenReturn(System.out);
        final TestPrePostClean instance = new TestPrePostClean();
        instance.setBefore(true);
        CommonConfigTest.stubConfig(mockMaster, NodeSelection.HISTORY_ONLY, true, false, null, 0L);
        final Launcher mockLauncher = mock(Launcher.class);

        // When
        instance.setUp(mockCurrentBuild, mockLauncher, mockListener);

        // Then
        verify(instance.mock).deleteWorkspaceOn(mockListener, normalisedMasterName, masterNormalWs);
        verify(instance.mock).deleteWorkspaceOn(mockListener, node3Name, node3NormalWs);
        verify(instance.mock).deleteWorkspaceOn(mockListener, node3Name, node3WeirdWs);
        verify(instance.mock).deleteWorkspaceOn(mockListener, node4Name, node4NormalWs);
    }

    @Test
    public void deleteWssInSeriesGivenWorkspacesThenDeletesInOrder() throws InterruptedException, IOException {
        // Given
        final AbstractBuild mockCurrentBuild = mock(AbstractBuild.class, "mockCurrentBuild");
        final String masterName = "";
        final String normalizedMasterName = "master";
        final String node1Name = "nodeC1";
        final String node2Name = "nodeC2";
        final String node3Name = "nodeC3WasDeletedUnderOurFeet";
        final String ws1 = "/Cabc";
        final String ws2 = "/Cdef";
        final Node mockNode1 = mockNode("mockNode1", node1Name, true);
        final Node mockNode2 = mockNode("mockNode2", node2Name, true);
        final Jenkins mockJenkins = mockNode(Jenkins.class, "mockJenkins", "", true);
        final FilePath masterws2 = mockJenkins.createPath(ws2);
        final FilePath node1ws1 = mockNode1.createPath(ws1);
        final FilePath node1ws2 = mockNode1.createPath(ws2);
        final FilePath node2ws1 = mockNode2.createPath(ws1);
        whenJenkinsGetNode(mockJenkins, mockNode1, mockNode2);
        final BuildListener mockListener = mock(BuildListener.class, "mockListener");
        when(mockListener.getLogger()).thenReturn(System.out);
        final Multimap<String, String> workspacesToBeRemoved = TreeMultimap.create();
        workspacesToBeRemoved.put(masterName, ws2);
        workspacesToBeRemoved.put(node1Name, ws1);
        workspacesToBeRemoved.put(node1Name, ws2);
        workspacesToBeRemoved.put(node2Name, ws1);
        workspacesToBeRemoved.put(node3Name, ws1);

        // When
        final TestPrePostClean instance = new TestPrePostClean();
        instance.deleteWssInSeries(mockCurrentBuild, mockJenkins, workspacesToBeRemoved, mockListener);

        // Then
        final InOrder inOrder = inOrder(instance.mock);
        inOrder.verify(instance.mock).deleteWorkspaceOn(mockListener, normalizedMasterName, masterws2);
        inOrder.verify(instance.mock).deleteWorkspaceOn(mockListener, node1Name, node1ws1);
        inOrder.verify(instance.mock).deleteWorkspaceOn(mockListener, node1Name, node1ws2);
        inOrder.verify(instance.mock).deleteWorkspaceOn(mockListener, node2Name, node2ws1);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void deleteWssInParallelGivenWorkspacesThenDeletesOnEachNodeInParallel()
            throws InterruptedException, IOException {
        // Given
        final AbstractBuild mockCurrentBuild = mock(AbstractBuild.class, "mockCurrentBuild");
        final String masterName = "";
        final String normalizedMasterName = "master";
        final String node1Name = "nodeD1";
        final String node2Name = "nodeD2";
        final String node3Name = "nodeD3WasDeletedUnderOurFeet";
        final String node4Name = "nodeD4";
        final String node5Name = "nodeD5";
        final String ws1 = "/Dabc";
        final String ws2 = "/Ddef";
        final Jenkins mockJenkins = mockNode(Jenkins.class, "mockJenkins", "", true);
        final Node mockNode1 = mockNode("mockNode1", node1Name, true);
        final Node mockNode2 = mockNode("mockNode2", node2Name, true);
        final Node mockNode4 = mockNode("mockNode4", node4Name, true);
        final Node mockNode5 = mockNode("mockNode5", node5Name, true);
        final FilePath masterws2 = mockJenkins.createPath(ws2);
        final FilePath node1ws1 = mockNode1.createPath(ws1);
        final FilePath node2ws1 = mockNode2.createPath(ws1);
        final FilePath node2ws2 = mockNode2.createPath(ws2);
        final FilePath node4ws1 = mockNode4.createPath(ws1);
        final FilePath node5ws2 = mockNode5.createPath(ws2);
        final TestPrePostClean instance = new TestPrePostClean();
        final BuildListener mockListener = mock(BuildListener.class, "mockListener");
        when(mockListener.getLogger()).thenReturn(System.out);
        final long millisecondsRequiredToDeleteAWorkspace = 200L;
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Thread.sleep(millisecondsRequiredToDeleteAWorkspace);
                return null;
            }
        }).when(instance.mock).deleteWorkspaceOn(any(), anyString(), any());
        whenJenkinsGetNode(mockJenkins, mockNode1, mockNode2, mockNode4, mockNode5);
        final Multimap<String, String> workspacesToBeRemoved = TreeMultimap.create();
        workspacesToBeRemoved.put(masterName, ws2);
        workspacesToBeRemoved.put(node1Name, ws1);
        workspacesToBeRemoved.put(node2Name, ws1);
        workspacesToBeRemoved.put(node2Name, ws2);
        workspacesToBeRemoved.put(node3Name, ws1);
        workspacesToBeRemoved.put(node4Name, ws1);
        workspacesToBeRemoved.put(node5Name, ws2);

        // When
        final ExecutorService parallelExecutor = Executors.newFixedThreadPool(6);
        final long timestampBeforeDeletion = System.currentTimeMillis();
        instance.deleteWssInParallel(mockCurrentBuild, mockJenkins, parallelExecutor, workspacesToBeRemoved,
                mockListener);
        final long timestampAfterDeletion = System.currentTimeMillis();

        // Then
        verify(instance.mock).deleteWorkspaceOn(mockListener, normalizedMasterName, masterws2);
        verify(instance.mock).deleteWorkspaceOn(mockListener, node1Name, node1ws1);
        final InOrder inOrder = inOrder(instance.mock);
        inOrder.verify(instance.mock).deleteWorkspaceOn(mockListener, node2Name, node2ws1);
        inOrder.verify(instance.mock).deleteWorkspaceOn(mockListener, node2Name, node2ws2);
        verify(instance.mock).deleteWorkspaceOn(mockListener, node4Name, node4ws1);
        verify(instance.mock).deleteWorkspaceOn(mockListener, node5Name, node5ws2);
        verifyNoMoreInteractions(instance.mock);
        final long timeTakenForDeletions = timestampAfterDeletion - timestampBeforeDeletion;
        final long minTimeItCanTakeIsBothDeletionsOnNode2 = millisecondsRequiredToDeleteAWorkspace * 2;
        final long maxExpectedTimeItShouldTakeIsNotMuchMore = minTimeItCanTakeIsBothDeletionsOnNode2
                + millisecondsRequiredToDeleteAWorkspace / 2;
        assertThat(timeTakenForDeletions, is(both(greaterThanOrEqualTo(minTimeItCanTakeIsBothDeletionsOnNode2))
                .and(lessThan(maxExpectedTimeItShouldTakeIsNotMuchMore))));
    }

    @Test
    public void deleteWssInParallelGivenInterruptThenAbandonsAllDeletionsImmediately()
            throws InterruptedException, IOException {
        // Given
        final AbstractBuild mockCurrentBuild = mock(AbstractBuild.class, "mockCurrentBuild");
        final String node1Name = "nodeE1";
        final String node2Name = "nodeE2";
        final String node3Name = "nodeE3";
        final String ws = "/Eabc";
        final Node mockNode1 = mockNode("mockNode1", node1Name, true);
        final Node mockNode2 = mockNode("mockNode2", node2Name, true);
        final Node mockNode3 = mockNode("mockNode3", node3Name, true);
        final FilePath node1ws = mockNode1.createPath(ws);
        final FilePath node2ws = mockNode2.createPath(ws);
        final FilePath node3ws = mockNode3.createPath(ws);
        final BuildListener mockListener = mock(BuildListener.class, "mockListener");
        when(mockListener.getLogger()).thenReturn(System.out);
        final TestPrePostClean instance = new TestPrePostClean();
        final long timeEachDeletionWillRunForUnlessCancelled = 1000L;
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Thread.sleep(timeEachDeletionWillRunForUnlessCancelled);
                return null;
            }
        }).when(instance.mock).deleteWorkspaceOn(mockListener, node1Name, node1ws);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Thread.sleep(timeEachDeletionWillRunForUnlessCancelled);
                return null;
            }
        }).when(instance.mock).deleteWorkspaceOn(mockListener, node2Name, node2ws);
        final Thread testThread = Thread.currentThread();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                System.out.println("Interrupting main thread");
                testThread.interrupt(); // interrupt the cleanup thread
                Thread.sleep(timeEachDeletionWillRunForUnlessCancelled);
                return null;
            }
        }).when(instance.mock).deleteWorkspaceOn(mockListener, node3Name, node3ws);
        final Jenkins mockJenkins = mock(Jenkins.class, "mockJenkins");
        whenJenkinsGetNode(mockJenkins, mockNode1, mockNode2, mockNode3);
        final Multimap<String, String> workspacesToBeRemoved = TreeMultimap.create();
        workspacesToBeRemoved.put(node1Name, ws);
        workspacesToBeRemoved.put(node2Name, ws);
        workspacesToBeRemoved.put(node3Name, ws);

        // When
        final ExecutorService parallelExecutor = Executors.newFixedThreadPool(6);
        final long timestampBeforeDeletion = System.currentTimeMillis();
        try {
            instance.deleteWssInParallel(mockCurrentBuild, mockJenkins, parallelExecutor, workspacesToBeRemoved,
                    mockListener);
            fail("Expecting to be interrupted");
        } catch (InterruptedException ex) {
            // expected
        }
        final long timestampAfterDeletion = System.currentTimeMillis();

        // Then
        final long timeTakenForDeletions = timestampAfterDeletion - timestampBeforeDeletion;
        assertThat(timeTakenForDeletions, lessThan(timeEachDeletionWillRunForUnlessCancelled));
    }

    private static AbstractBuild mockBuild(final String mockName, final Node builtOnNode, final String wsLocation,
            final boolean hasntStartedYet, final boolean hasExecutor)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final String buildOnNodeName = builtOnNode == null ? "" : builtOnNode.getNodeName();
        final AbstractBuild m = mock(AbstractBuild.class, mockName);
        final Field wsField = AbstractBuild.class.getDeclaredField("workspace");
        wsField.setAccessible(true);
        wsField.set(m, wsLocation);
        when(m.getBuiltOn()).thenReturn(builtOnNode);
        when(m.getBuiltOnStr()).thenReturn(buildOnNodeName);
        when(m.hasntStartedYet()).thenReturn(hasntStartedYet);
        when(m.getExecutor()).thenReturn(hasExecutor ? mock(Executor.class, mockName + "_executor") : null);
        return m;
    }

    private static Node mockNode(final String mockName, final String nodeName, boolean nodeIsOnline) {
        return mockNode(Node.class, mockName, nodeName, nodeIsOnline);
    }

    private static <T extends Node> T mockNode(final Class<T> type, final String mockName, final String nodeName,
            boolean nodeIsOnline) {
        final T m = mock(type, mockName);
        when(m.getNodeName()).thenReturn(nodeName);
        if (nodeIsOnline) {
            final VirtualChannel mvc = type == Jenkins.class ? null : mock(VirtualChannel.class, mockName + "_vc");
            when(m.createPath(anyString())).thenAnswer(new Answer<FilePath>() {
                @Override
                public FilePath answer(final InvocationOnMock invocation) throws Throwable {
                    final String remotePath = (String) invocation.getArguments()[0];
                    return new FilePath(mvc, remotePath);
                }
            });
        } else {
            when(m.createPath(anyString())).thenReturn(null);
        }
        return m;
    }

    private static void whenJenkinsGetNode(final Jenkins mockJenkins, Node... nodes) {
        for (final Node n : nodes) {
            final String nodeName = n.getNodeName();
            assert nodeName != null && !nodeName.isEmpty();
            when(mockJenkins.getNode(nodeName)).thenReturn(n);
        }
        when(mockJenkins.getNodes()).thenReturn(Arrays.asList(nodes));
    }

    private interface IMockableMethods {
        void deleteWorkspaceOn(BuildListener listener, String nodeName, FilePath fp) throws InterruptedException;
    }

    class TestPrePostClean extends PrePostClean {
        final IMockableMethods mock = mock(IMockableMethods.class);

        @Override
        void deleteWorkspaceOn(AbstractBuild<?, ?> build, BuildListener listener, String nodeName, FilePath fp)
                throws InterruptedException {
            mock.deleteWorkspaceOn(listener, nodeName, fp);
        }
    }
}
