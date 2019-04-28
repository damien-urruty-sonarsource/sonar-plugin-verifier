package com.jetbrains.pluginverifier.verifiers

import com.jetbrains.pluginverifier.verifiers.resolution.ClassFile
import com.jetbrains.pluginverifier.verifiers.resolution.ClassResolver
import java.util.*

private fun ClassResolver.resolveAllDirectParents(classFile: ClassFile): List<ClassFile> {
  val parents = listOfNotNull(classFile.superName) + classFile.interfaces
  return parents.mapNotNull { resolveClassOrNull(it) }
}

fun ClassResolver.isSubclassOrSelf(childClassName: String, possibleParentName: String): Boolean {
  if (childClassName == possibleParentName) {
    return true
  }
  return isSubclassOf(childClassName, possibleParentName)
}

fun ClassResolver.isSubclassOf(childClassName: String, possibleParentName: String): Boolean {
  val childClass = resolveClassOrNull(childClassName) ?: return false
  return isSubclassOf(childClass, possibleParentName)
}

fun ClassResolver.isSubclassOf(child: ClassFile, parentName: String): Boolean {
  if (parentName == "java/lang/Object") {
    return true
  }

  val directParents = resolveAllDirectParents(child)

  val queue = LinkedList<ClassFile>()
  queue.addAll(directParents)

  val visited = hashSetOf<String>()
  visited.addAll(directParents.map { it.name })

  while (queue.isNotEmpty()) {
    val node = queue.poll()
    if (node.name == parentName) {
      return true
    }

    resolveAllDirectParents(node).filterNot { it.name in visited }.forEach {
      visited.add(it.name)
      queue.addLast(it)
    }
  }

  return false
}