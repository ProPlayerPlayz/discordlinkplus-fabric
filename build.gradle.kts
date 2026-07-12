plugins {
    java
    id("com.gradleup.shadow") version "9.4.2"
}

group = property("group") as String
version = (findProperty("releaseVersion") as String?) ?: (property("pluginVersion") as String)

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.maxhenkel.de/repository/public")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.13")

    implementation("net.dv8tion:JDA:6.4.2") {
        exclude(module = "opus-java")
    }
    implementation("club.minnced:jdave-api:0.1.8")
    runtimeOnly("club.minnced:jdave-native-darwin:0.1.8")
    runtimeOnly("club.minnced:jdave-native-linux-x86-64:0.1.8")
    runtimeOnly("club.minnced:jdave-native-linux-aarch64:0.1.8")
    runtimeOnly("club.minnced:jdave-native-win-x86-64:0.1.8")

    implementation("com.cjcrafter:foliascheduler:0.7.0")
    implementation("com.google.code.gson:gson:2.13.1")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
    )
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("DiscordLinkPlus-${project.version}.jar")
    relocate("com.cjcrafter.foliascheduler", "com.mcdiscordbot.lib.foliascheduler")
    relocate("club.minnced", "com.mcdiscordbot.lib.minnced")
    relocate("com.google.gson", "com.mcdiscordbot.lib.gson")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    enabled = false
}
