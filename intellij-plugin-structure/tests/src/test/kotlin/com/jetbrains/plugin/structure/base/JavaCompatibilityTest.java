package com.jetbrains.plugin.structure.base;

import com.jetbrains.plugin.structure.intellij.plugin.SonarPluginManager;
import com.jetbrains.plugin.structure.intellij.resources.ResourceResolver;
import org.junit.Test;

import java.io.File;

public class JavaCompatibilityTest {
  @SuppressWarnings("deprecation")
  @Test
  public void sourceCompatibility() {
    // The following "createManager" signatures must not be changed.
    SonarPluginManager.createManager();
    SonarPluginManager.createManager(new File(""));
    ResourceResolver resourceResolver = (relativePath, basePath) -> {
      throw new UnsupportedOperationException("");
    };
    SonarPluginManager.createManager(resourceResolver);
    SonarPluginManager.createManager(resourceResolver, new File(""));
  }
}
