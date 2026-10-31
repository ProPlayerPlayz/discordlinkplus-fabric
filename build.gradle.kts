plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.19.3"
    id("com.gradleup.shadow") version "9.4.2"
}
group = "com.mcdiscordbot"
version = "1.0.2-fabric.26.3"
base { archivesName.set("DiscordLinkPlus-Voice") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)); withSourcesJar() }
sourceSets {
    main { java.setSrcDirs(listOf("src/fabric/java")); resources.setSrcDirs(listOf("src/fabric/resources")) }
}
val bundled by configurations.creating
configurations.implementation { extendsFrom(bundled) }
repositories {
    mavenCentral()
    maven("https://maven.maxhenkel.de/repository/public")
    maven("https://api.modrinth.com/maven")
}
dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation("net.fabricmc.fabric-api:fabric-api:0.165.0+26.3")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.27")
    compileOnly("maven.modrinth:simple-voice-chat:fabric-2.6.27+26.3")
    bundled("net.dv8tion:JDA:6.7.0") { exclude(group = "org.slf4j") }
    bundled("club.minnced:jdave-api:0.1.8")
    bundled("club.minnced:jdave-native-linux-x86-64:0.1.8")
    bundled("club.minnced:jdave-native-linux-aarch64:0.1.8")
    bundled("club.minnced:jdave-native-win-x86-64:0.1.8")
    bundled("club.minnced:jdave-native-darwin:0.1.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("maven.modrinth:simple-voice-chat:fabric-2.6.27+26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.withType<JavaCompile>().configureEach { options.release.set(25); options.encoding = "UTF-8" }
tasks.processResources { inputs.property("version", project.version); filesMatching("fabric.mod.json") { expand("version" to project.version) } }
tasks.test { useJUnitPlatform() }
tasks.shadowJar {
    configurations = listOf(bundled)
    archiveClassifier.set("")
    relocate("net.dv8tion", "com.mcdiscordbot.voice.lib.jda")
    relocate("club.minnced", "com.mcdiscordbot.voice.lib.minnced")
    relocate("okhttp3", "com.mcdiscordbot.voice.lib.okhttp3")
    relocate("okio", "com.mcdiscordbot.voice.lib.okio")
    relocate("com.neovisionaries", "com.mcdiscordbot.voice.lib.neovisionaries")
    relocate("gnu.trove", "com.mcdiscordbot.voice.lib.trove")
    relocate("com.fasterxml.jackson", "com.mcdiscordbot.voice.lib.jackson")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class", "META-INF/versions/**/module-info.class")
    from("PORT-NOTICE.txt")
}
tasks.jar { archiveClassifier.set("unbundled") }
tasks.build { dependsOn(tasks.shadowJar) }
