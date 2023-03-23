package com.jetbrains.pluginverifier.tasks.checkPluginApi

import com.jetbrains.plugin.structure.intellij.version.Version

enum class SonarSourcePlugin(val key: String, latestVersion: String) {
    SONAR_JAVA("java", "7.16.1.31255"),
    SONAR_PYTHON("python", "4.1.0.11333"),
    SONAR_JS("javascript", "10.0.1.20755"),
    SONAR_PHP("php", "3.28.0.9490");

    val version: Version = Version.createIdeVersion(latestVersion)

}
