/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.sonar.plugin.api;

import com.jetbrains.plugin.structure.intellij.version.Version;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

class SonarPluginApiImpl extends SonarPluginApi {

  private final Version myVersion;
  private final Path myIdePath;

  SonarPluginApiImpl(@NotNull Path idePath,
          @NotNull Version version) {
    myIdePath = idePath;
    myVersion = version;
  }

  @NotNull
  @Override
  public Version getVersion() {
    return myVersion;
  }

  @NotNull
  @Override
  public Path getIdePath() {
    return myIdePath;
  }

  @Override
  public String toString() {
    return myVersion.asString();
  }

}
