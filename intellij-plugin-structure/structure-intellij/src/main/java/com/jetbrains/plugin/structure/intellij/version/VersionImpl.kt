/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.intellij.version

import java.util.*

class VersionImpl(
  private val string: String
) : Version() {

  override fun asString(): String {
    return string
  }

  override fun toString() = asString()

  override fun hashCode(): Int {
    return string.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other is Version) {
      return string == other.asString()
    }
    return false
  }

  companion object {

    @Throws(IllegalArgumentException::class)
    fun fromString(version: String): VersionImpl {
      if (version.isBlank()) {
        throw IllegalArgumentException("sonar-plugin-api version string must not be empty")
      }
      return VersionImpl(version)
    }
  }
}
