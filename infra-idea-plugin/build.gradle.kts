plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

val localIdePath = providers.gradleProperty("localIdePath")

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("jvmTarget").get().toInt()))
    }
}

dependencies {
    intellijPlatform {
        if (localIdePath.isPresent) {
            local(localIdePath.get())
        } else {
            create(
                providers.gradleProperty("platformType"),
                providers.gradleProperty("platformVersion"),
            )
        }

        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.properties")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.spring")
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName").get()
        version = providers.gradleProperty("pluginVersion").get()

        ideaVersion {
            sinceBuild = providers.gradleProperty("sinceBuild").get()
            untilBuild = providers.gradleProperty("untilBuild").get()
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        sourceCompatibility = providers.gradleProperty("jvmTarget").get()
        targetCompatibility = providers.gradleProperty("jvmTarget").get()
        options.encoding = "UTF-8"
    }
}
