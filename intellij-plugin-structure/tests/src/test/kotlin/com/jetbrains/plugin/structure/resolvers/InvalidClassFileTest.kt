package com.jetbrains.plugin.structure.resolvers

import com.jetbrains.plugin.structure.base.contentBuilder.buildDirectory
import com.jetbrains.plugin.structure.base.contentBuilder.buildZipFile
import com.jetbrains.plugin.structure.classes.resolvers.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests that [InvalidClassFileException] is thrown on attempts to read invalid class files.
 */
class InvalidClassFileTest {

  @Rule
  @JvmField
  val temporaryFolder = TemporaryFolder()

  private object InvalidFileOrigin : FileOrigin {
    override val parent: FileOrigin? = null
  }

  @Test
  fun `read invalid class file from local class file fails in constructor`() {
    val classFilesRoot = buildDirectory(temporaryFolder.newFolder()) {
      file("invalid.class", "bad")
    }
    try {
      ClassFilesResolver(classFilesRoot.toPath(), InvalidFileOrigin).use { }
    } catch (e: InvalidClassFileException) {
      assertTrue(e.message.startsWith("Unable to read class 'invalid' using the ASM Java Bytecode engineering library. The internal ASM error: java.lang.ArrayIndexOutOfBoundsException"))
      return
    }
    fail()
  }

  @Test
  fun `read invalid class file from jar`() {
    val jarFile = buildZipFile(temporaryFolder.newFile("invalid.jar")) {
      file("invalid.class", "bad")
    }

    JarFileResolver(jarFile.toPath(), Resolver.ReadMode.FULL, InvalidFileOrigin).use { jarResolver ->
      val invalidResult = jarResolver.resolveClass("invalid") as ResolutionResult.InvalidClassFile
      assertTrue(invalidResult.message.startsWith("Unable to read class 'invalid' using the ASM Java Bytecode engineering library. The internal ASM error: java.lang.ArrayIndexOutOfBoundsException"))
    }
  }
}