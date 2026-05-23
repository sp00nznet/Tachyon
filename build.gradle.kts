import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Apply the java plugin to add support for Java
    java

    // Apply the application plugin to add support for building an application
    application

    id("org.jetbrains.kotlin.jvm") version "1.9.10"

    // Make JMH available for performance experiments
    id("me.champeau.jmh") version "0.7.2"
}

repositories {
    mavenCentral()
}

val lwjglNatives = listOf(
    // No 32-bit support, as nothing uses it.
    "natives-windows-arm64",
    "natives-windows",

    "natives-macos-arm64",
    "natives-macos",

    "natives-linux-arm64",
    "natives-linux",
)

dependencies {
    // We use Slipstream as a library, to patch in mods on-the-fly
    implementation(project(":slipstream"))

    implementation("org.slick2d:slick2d-core:1.0.2") {
        // We're only using a few things from Slick, like its image and audio
        // decoders, so we don't need all it's native libraries. In fact, we
        // really shouldn't: we don't want both LWJGL 2 and 3!
        isTransitive = false
    }

    // There's a few libraries that Slick uses that we do need, though.
    implementation("org.jcraft:jorbis:0.0.17")

    // JDOM is our XML parser
    implementation("org.jdom:jdom2:2.0.6.1")

    // Use JUnit test framework
    testImplementation("junit:junit:4.13.1")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    implementation(platform("org.lwjgl:lwjgl-bom:3.3.2"))

    implementation("org.lwjgl", "lwjgl")
    implementation("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-openal")
    implementation("org.lwjgl", "lwjgl-opengl")
    // Native file-open dialog (used to point Tachyon at ftl.dat manually).
    implementation("org.lwjgl", "lwjgl-tinyfd")

    // Include all the platform-specific libraries, so our fatJar can run
    // on any supported platform.
    for (platform in lwjglNatives) {
        runtimeOnly("org.lwjgl", "lwjgl", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-openal", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-tinyfd", classifier = platform)
    }
}

// Define the main class for the application
application {
    mainClass.set("xyz.znix.xftl.App")

    // On Mac, all UI stuff must happen on the first thread, including
    // our GLFW stuff.
    // This option doesn't exist on other platforms.
    if (System.getProperty("os.name").contains("Mac OS")) {
        applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
    }
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

    // This is already properly added to runtimeClasspath, but it's not set
    // to automatically build - compileJava will run, but not the jar task.
    dependsOn(":slipstream:jar")

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    archiveBaseName.set("XFTL-complete")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Use our sources first, so we can replace Slick classes
    from(sourceSets.main.get().output)

    from(configurations.runtimeClasspath.get().map { file ->
        // Exclude signature files, since if the jar is signed
        // we have to do the whole thing at once.
        zipTree(file).matching {
            exclude("META-INF/*.SF")
            exclude("META-INF/*.RSA")
        }
    })

    // Ship the project license + attribution at the jar root, so GPL §3 is
    // visibly satisfied alongside the binary.
    from(rootProject.file("LICENCE.txt"))
    from(rootProject.file("NOTICE.md"))
}

/**
 * Build a runnable Windows app image: build/dist/Tachyon/Tachyon.exe plus a
 * trimmed-down JRE alongside it. The user double-clicks Tachyon.exe - no
 * separate Java install required. jpackage ships with JDK 14+; the task
 * only works when run on Windows (jpackage cross-packaging isn't supported).
 */
val packageWindows by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Build a standalone Tachyon.exe + bundled JRE in build/dist/Tachyon."
    dependsOn(fatJar)

    val inputDir = layout.buildDirectory.dir("libs")
    val outputDir = layout.buildDirectory.dir("dist")

    doFirst {
        // jpackage refuses to write into an existing output directory.
        val existing = outputDir.get().asFile.resolve("Tachyon")
        if (existing.exists()) existing.deleteRecursively()
        outputDir.get().asFile.mkdirs()
    }

    commandLine(
        "jpackage",
        "--type", "app-image",
        "--input", inputDir.get().asFile.absolutePath,
        "--main-jar", "XFTL-complete.jar",
        "--main-class", application.mainClass.get(),
        "--name", "Tachyon",
        "--dest", outputDir.get().asFile.absolutePath,
        // Hide the console window so double-clicking Tachyon.exe just opens
        // the game (--win-console would keep a black box around for stdout).
    )

    doLast {
        val tachyonDir = outputDir.get().asFile.resolve("Tachyon")
        // Drop the project license and the attribution NOTICE at the top of
        // the bundle so the GPL §3 distribution requirements are visible
        // without poking inside the jar or the bundled JRE's legal/ folder.
        copy {
            from(rootProject.file("LICENCE.txt"))
            from(rootProject.file("NOTICE.md"))
            into(tachyonDir)
        }
        val exe = tachyonDir.resolve("Tachyon.exe")
        if (exe.exists()) {
            logger.lifecycle("Built ${exe.absolutePath}")
        }
    }
}
