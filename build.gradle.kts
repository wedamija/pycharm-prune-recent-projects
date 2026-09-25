plugins {
    kotlin("jvm") version "2.3.20"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// plugin.kts needs the IDE classpath, so only Prune.kt is compiled here.
kotlin.sourceSets {
    main { kotlin.setSrcDirs(listOf("plugin")); kotlin.exclude("plugin.kts") }
    test { kotlin.setSrcDirs(listOf("test")) }
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
