/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.ide;

import com.jetbrains.plugin.structure.intellij.version.Version;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * A sonar-plugin-api instance.
 * Can be created via {@link SonarPluginApiManager#createSonarPluginApi(java.nio.file.Path)}.
 */
public abstract class SonarPluginApi {
  /**
   * Returns the IDE version either from 'build.txt' or specified with {@link SonarPluginApiManager#createSonarPluginApi(java.nio.file.Path, Version)}
   *
   * @return ide version of {@code this} instance
   */
  @NotNull
  public abstract Version getVersion();

  /**
   * Returns the file from which {@code this} Ide obtained.
   *
   * @return the path to the Ide instance
   */
  @NotNull
  public abstract Path getIdePath();
}
