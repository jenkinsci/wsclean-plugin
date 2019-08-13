package de.jamba.hudson.plugin.wsclean;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Set;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.collect.ImmutableSet;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TopLevelItem;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildWrapper.Environment;
import jenkins.model.Jenkins;

@SuppressWarnings("rawtypes")
public class PrePostCleanTest {
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
        final TestPrePostClean instance = new TestPrePostClean();
        instance.setBefore(false);
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
        final TestPrePostClean instance = new TestPrePostClean();
        instance.setBefore(true);
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
        final TestPrePostClean instance = new TestPrePostClean();
        instance.setBefore(true);
        final AbstractBuild mockBuild = mock(AbstractBuild.class, "mockBuild");
        final Launcher mockLauncher = mock(Launcher.class);
        final BuildListener mockListener = mock(BuildListener.class, "mockListener");
        when(mockListener.getLogger()).thenReturn(System.out);
        final AbstractProject mockProject = mock(AbstractProject.class,
                withSettings().name("mockProject").extraInterfaces(TopLevelItem.class));
        when(mockBuild.getBuiltOnStr()).thenReturn("someNode");
        when(mockBuild.getProject()).thenReturn(mockProject);
        when(mockProject.getAssignedLabel()).thenReturn(null); // roaming

        // When
        final Environment env = instance.setUp(mockBuild, mockLauncher, mockListener);

        // Then
        verifyNoMoreInteractions(instance.mock);
        env.tearDown(mockBuild, mockListener);
        verifyNoMoreInteractions(instance.mock);
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

    private interface IMockableMethods {
        void deleteWorkspaceOn(BuildListener listener, String nodeName, FilePath fp) throws InterruptedException;
    }

    class TestPrePostClean extends PrePostClean {
        final IMockableMethods mock = mock(IMockableMethods.class);

        @Override
        void deleteWorkspaceOn(BuildListener listener, String nodeName, FilePath fp) throws InterruptedException {
            mock.deleteWorkspaceOn(listener, nodeName, fp);
        }
    }
}
