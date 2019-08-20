package de.jamba.hudson.plugin.wsclean;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.regex.Pattern;

import org.junit.Test;

import de.jamba.hudson.plugin.wsclean.CommonConfig.NodeSelection;
import hudson.DescriptorExtensionList;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.model.TestJenkins;

public class CommonConfigTest {

    @Test
    public void constructorGivenDefaultsThenReturnsDefaultInstance() throws Exception {
        // Given
        final String[] expectedNodeNamesToSkip = new String[0];
        final Pattern[] expectedNodeNamesToSkipPatterns = new Pattern[0];
        final String expectedNodeNamesToSkipString = "";
        final NodeSelection expectedNodeSelection = NodeSelection.LABEL_ONLY;
        final boolean expectedParallel = true;
        final boolean expectedSkipRoaming = true;
        final Long expectedTimeoutInMilliseconds = 900000L;

        // When
        final CommonConfig instance = new CommonConfig() {
            @Override
            public synchronized void load() {
                // do nothing
            }
        };
        final String[] actualNodeNamesToSkip = instance.getNodeNamesToSkip();
        final Pattern[] actualNodeNamesToSkipPatterns = instance.getNodeNamesToSkipPatterns();
        final String actualNodeNamesToSkipString = instance.getNodeNamesToSkipString();
        final NodeSelection actualNodeSelection = instance.getNodeSelection();
        final boolean actualParallel = instance.getParallel();
        final boolean actualSkipRoaming = instance.getSkipRoaming();
        final Long actualTimeoutInMilliseconds = instance.getTimeoutInMilliseconds();

        // Then
        assertThat(actualNodeNamesToSkip, equalTo(expectedNodeNamesToSkip));
        assertThat(actualNodeNamesToSkipPatterns, equalTo(expectedNodeNamesToSkipPatterns));
        assertThat(actualNodeNamesToSkipString, equalTo(expectedNodeNamesToSkipString));
        assertThat(actualNodeSelection, equalTo(expectedNodeSelection));
        assertThat(actualParallel, equalTo(expectedParallel));
        assertThat(actualSkipRoaming, equalTo(expectedSkipRoaming));
        assertThat(actualTimeoutInMilliseconds, equalTo(expectedTimeoutInMilliseconds));
    }

    @Test
    public void setNodeNamesToSkipGivenNamesThenAlsoSetsPatterns() throws Exception {
        // Given
        final String expectedNodeNamesToSkipString = "foo\n(((invalidRegex\nbar";
        final String[] expectedNodeNamesToSkip = new String[] { "foo", "(((invalidRegex", "bar" };
        final Pattern[] expectedNodeNamesToSkipPatterns = new Pattern[] { Pattern.compile("foo"),
                Pattern.compile("bar") };
        final CommonConfig instance = new CommonConfig() {
            @Override
            public void load() {
            }

            @Override
            public void save() {
            }
        };
        // When
        instance.setNodeNamesToSkipString(expectedNodeNamesToSkipString);

        // Then
        final String[] actualNodeNamesToSkip = instance.getNodeNamesToSkip();
        final Pattern[] actualNodeNamesToSkipPatterns = instance.getNodeNamesToSkipPatterns();
        final String actualNodeNamesToSkipString = instance.getNodeNamesToSkipString();

        // Then
        assertThat(actualNodeNamesToSkip, equalTo(expectedNodeNamesToSkip));
        assertThat(actualNodeNamesToSkipString, equalTo(expectedNodeNamesToSkipString));
        assertThat(actualNodeNamesToSkipPatterns.length, equalTo(expectedNodeNamesToSkipPatterns.length));
        assertThat(actualNodeNamesToSkipPatterns[0].pattern(), equalTo(expectedNodeNamesToSkipPatterns[0].pattern()));
        assertThat(actualNodeNamesToSkipPatterns[1].pattern(), equalTo(expectedNodeNamesToSkipPatterns[1].pattern()));
    }

    @SuppressWarnings("unchecked")
    static void stubConfig(Jenkins mockJenkins, CommonConfig config) {
        final DescriptorExtensionList<GlobalConfiguration, GlobalConfiguration> extensionList = mock(
                DescriptorExtensionList.class);
        when(extensionList.get(CommonConfig.class)).thenReturn(config);
        when(mockJenkins.<GlobalConfiguration, GlobalConfiguration>getDescriptorList(GlobalConfiguration.class))
                .thenReturn(extensionList);
        TestJenkins.setJenkinsInstance(mockJenkins);
    }

    static void stubConfig(Jenkins mockJenkins, NodeSelection nodeSelection, boolean skipRoaming, boolean parallel,
            String[] nodeNamesToSkip, long timeoutInMilliseconds) {
        final CommonConfig instance = new CommonConfig() {
            @Override
            public void load() {
            }

            @Override
            public void save() {
            }
        };
        instance.setNodeSelection(nodeSelection);
        instance.setSkipRoaming(skipRoaming);
        instance.setParallel(parallel);
        instance.setNodeNamesToSkip(nodeNamesToSkip);
        instance.setTimeoutInMilliseconds(timeoutInMilliseconds);
        stubConfig(mockJenkins, instance);
    }

}
