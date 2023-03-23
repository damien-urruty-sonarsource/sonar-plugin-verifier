/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.sonar.plugin.api;

import com.jetbrains.plugin.structure.intellij.version.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Factory class which creates the {@link SonarPluginApi} instances.
 */
public abstract class SonarPluginApiManager {

  @NotNull
  public static SonarPluginApiManager createManager() {
    return new SonarPluginApiManagerImpl();
  }

  /**
   * @param idePath Path to IDE
   * @return IDE instance
   * @throws IOException if IO error occurs
   * @throws InvalidSonarPluginApiException if IDE is invalid
   * @deprecated use {@link #createSonarPluginApi(Path)}
   */
  @NotNull
  @Deprecated
  public final SonarPluginApi createSonarPluginApi(@NotNull File idePath) throws IOException, InvalidSonarPluginApiException {
    return createSonarPluginApi(idePath.toPath());
  }

  /**
   * Creates the {@code IDE} instance from the specified directory. IDE may be in the distribution form (a set of .jar
   * files) or in the source code form with the compiled classes.
   *
   * @param idePath an IDE home directory
   * @return created IDE instance
   * @throws IOException         if io-error occurs
   * @throws InvalidSonarPluginApiException if IDE by specified path is invalid
   */
  @NotNull
  public abstract SonarPluginApi createSonarPluginApi(@NotNull Path idePath) throws IOException, InvalidSonarPluginApiException;


  /**
   * @param idePath IDE path
   * @param version IDE version
   * @return IDE instance
   * @throws IOException if IO error occurs
   * @deprecated use {@link #createSonarPluginApi(Path, Version)}
   */
  @Deprecated
  @NotNull
  public final SonarPluginApi createSonarPluginApi(@NotNull File idePath, @Nullable Version version) throws IOException, InvalidSonarPluginApiException {
    return createSonarPluginApi(idePath.toPath(), version);
  }

  /**
   * Similar to the {@link #createSonarPluginApi(Path)} but updates a version of the created IDE to the specified one. By default
   * the version of the IDE is read from the 'build.txt'.
   *
   * @param idePath IDE home directory
   * @param version version of the IDE
   * @return created IDE instance
   * @throws IOException         if io-error occurs
   * @throws InvalidSonarPluginApiException if IDE by specified path is invalid
   */
  @NotNull
  public abstract SonarPluginApi createSonarPluginApi(@NotNull Path idePath, @Nullable Version version) throws IOException, InvalidSonarPluginApiException;

}
