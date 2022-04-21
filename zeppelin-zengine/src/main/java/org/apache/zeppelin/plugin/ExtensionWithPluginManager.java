package org.apache.zeppelin.plugin;

/**
 *
 * Indicates a plugin extension that requires the PluginManager for further operations. Extension that implements this interface
 * get the PluginManager passed in right after object creation.
 */
public interface ExtensionWithPluginManager {

  public void setPluginManager(IPluginManager pluginManager);

}
