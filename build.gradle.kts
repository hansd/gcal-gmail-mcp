plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    application
}

group = "com.hansdockter"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("io.ktor:ktor-server-core-jvm:2.3.7")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.7")
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.7")

    implementation("io.ktor:ktor-client-core-jvm:2.3.13")
    implementation("io.ktor:ktor-client-cio-jvm:2.3.7")

    implementation("com.google.api-client:google-api-client:2.4.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.36.0")
    implementation("com.google.apis:google-api-services-gmail:v1-rev20240520-2.0.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20241101-2.0.0")

    implementation("com.sun.mail:jakarta.mail:2.0.2")
    implementation("org.slf4j:slf4j-simple:2.0.12")
}

application {
    mainClass.set("com.hansdockter.mcp.gcalgmail.MainKt")
}

tasks {
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        archiveFileName.set("gcal-gmail-mcp.jar")
        manifest {
            attributes["Main-Class"] = "com.hansdockter.mcp.gcalgmail.MainKt"
            attributes["Implementation-Title"] = "gcal-gmail-mcp"
            attributes["Implementation-Version"] = project.version
        }
        from({
            configurations.runtimeClasspath.get().map { file ->
                if (file.isDirectory) file else zipTree(file)
            }
        })
    }

    register<Copy>("install") {
        dependsOn(jar)
        from(jar)
        into(System.getProperty("user.home") + "/.gcal-gmail-mcp")
        doLast {
            println("Installed to ~/.gcal-gmail-mcp/gcal-gmail-mcp.jar")
        }
    }
}

kotlin {
    jvmToolchain(21)
}
