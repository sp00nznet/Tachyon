import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Apply the java plugin to add support for Java
    java

    // Apply the application plugin to add support for building an application
    application

    id("org.jetbrains.kotlin.jvm") version "1.9.10"
}

repositories {
    mavenCentral()
}

dependencies {
    // This dependency is found on compile classpath of this component and consumers.
    implementation("org.slick2d:slick2d-core:1.0.2")

    implementation("org.jdom:jdom2:2.0.6.1")

    // Use JUnit test framework
    testImplementation("junit:junit:4.12")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

// Define the main class for the application
application {
    mainClass.set("xyz.znix.xftl.App")
}

java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val jar: Jar by tasks
val classes: Task by tasks

val fatJar by tasks.registering(Jar::class) {
    dependsOn(classes)
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    archiveBaseName.set("XFTL-complete")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Use our sources first, so we can replace Slick classes
    from(sourceSets.main.get().output)

    from(configurations.runtimeClasspath.get().map { zipTree(it) })
}
