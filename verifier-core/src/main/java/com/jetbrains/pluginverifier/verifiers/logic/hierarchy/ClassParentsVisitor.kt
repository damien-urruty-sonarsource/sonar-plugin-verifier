package com.jetbrains.pluginverifier.verifiers.logic.hierarchy

import com.jetbrains.pluginverifier.misc.singletonOrEmpty
import com.jetbrains.pluginverifier.verifiers.VerificationContext
import com.jetbrains.pluginverifier.verifiers.fromClass
import com.jetbrains.pluginverifier.verifiers.resolveClassOrProblem
import org.objectweb.asm.tree.ClassNode

class ClassParentsVisitor(private val context: VerificationContext,
                          private val visitInterfaces: Boolean) {

  companion object {
    const val JAVA_LANG_OBJECT = "java/lang/Object"
  }

  private val visitedClasses = hashSetOf<String>()

  fun visitClass(currentClass: ClassNode,
                 visitSelf: Boolean,
                 onEnter: (ClassNode) -> Boolean,
                 onExit: (ClassNode) -> Unit = {}) {
    visitedClasses.add(currentClass.name)

    if (visitSelf && !onEnter(currentClass)) {
      return
    }

    @Suppress("UNCHECKED_CAST")
    val interfaces = if (visitInterfaces) {
      currentClass.interfaces as List<String>
    } else {
      emptyList()
    }

    val superParents = currentClass.superName.singletonOrEmpty() + interfaces

    superParents
        .asSequence()
        .filterNot { it in visitedClasses }
        .mapNotNull { context.resolveClassOrProblem(it, currentClass, { context.fromClass(currentClass) }) }
        .forEach { visitClass(it, true, onEnter, onExit) }

    if (visitSelf) {
      onExit(currentClass)
    }
  }


}