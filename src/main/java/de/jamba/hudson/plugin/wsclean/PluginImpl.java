package de.jamba.hudson.plugin.wsclean;

import hudson.Plugin;
import hudson.tasks.BuildWrappers;

/**
 * Entry point of a plugin.
 *
 * @author Tom Spengler
 */
public class PluginImpl extends Plugin {
    public void start() throws Exception {
    	BuildWrappers.WRAPPERS.add(PrePostClean.DESCRIPTOR);
        
    }
}
