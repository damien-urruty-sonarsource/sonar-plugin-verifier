/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.intellij.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

public abstract class Version implements com.jetbrains.plugin.structure.base.utils.Version<Version> {

  /**
   * Tries to parse specified string as IDE version and throws an {@link IllegalArgumentException}
   * if the string is not a valid IDE version.
   *
   * @param version a string presentation of a version to be parsed
   * @return an instance of {@link Version}
   * @throws IllegalArgumentException if specified {@code version} doesn't represent correct {@code IdeVersion}
   * @see #createIdeVersionIfValid a version of the method that returns null instead of exception
   */
  @NotNull
  public static Version createIdeVersion(@NotNull String version) throws IllegalArgumentException {
    return VersionImpl.Companion.fromString(version);
  }

  /**
   * Tries to parse specified string as IDE version and returns null if not succeed.
   *
   * @param version a string presentation of a version to be parsed
   * @return instance of {@link Version} for specified string, or null
   * if the string is not a valid IDE version
   */
  @Nullable
  public static Version createIdeVersionIfValid(@NotNull String version) {
    try {
      return VersionImpl.Companion.fromString(version);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Override
  public final int compareTo(@NotNull Version other) {
    return asString().compareTo(other.asString());
  }

}
