plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.19.3"
    id("com.gradleup.shadow") version "9.4.2"
}
val targetMinecraft = providers.gradleProperty("minecraftVersion").get()
val targetJava = providers.gradleProperty("javaVersion").get().toInt()
val fabricLoaderVersion = providers.gradleProperty("loaderVersion").get()
val fabricApiVersion = providers.gradleProperty("fabricApiVersion").get()
val svcVersion = providers.gradleProperty("voicechatVersion").get()
val jdaVersion = providers.gradleProperty("jdaVersion").get()
val daveVersion = providers.gradleProperty("daveVersion").get()

group = "com.mcdiscordbot"
version = "${providers.gradleProperty("modVersion").get()}-fabric.$targetMinecraft"
base { archivesName.set("DiscordLinkPlus-Voice") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(targetJava)); withSourcesJar() }
sourceSets {
    main { java.setSrcDirs(listOf("src/fabric/java")); resources.setSrcDirs(listOf("src/fabric/resources")) }
}
val bundled = configurations.create("bundled")
configurations.implementation { extendsFrom(bundled) }
repositories {
    mavenCentral()
    maven("https://maven.maxhenkel.de/repository/public")
    maven("https://api.modrinth.com/maven")
}
dependencies {
    minecraft("com.mojang:minecraft:$targetMinecraft")
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:$svcVersion")
    compileOnly("maven.modrinth:simple-voice-chat:fabric-$svcVersion+$targetMinecraft")
    bundled("net.dv8tion:JDA:$jdaVersion") { exclude(group = "org.slf4j") }
    bundled("club.minnced:jdave-api:$daveVersion")
    bundled("club.minnced:jdave-native-linux-x86-64:$daveVersion")
    bundled("club.minnced:jdave-native-linux-aarch64:$daveVersion")
    bundled("club.minnced:jdave-native-win-x86-64:$daveVersion")
    bundled("club.minnced:jdave-native-darwin:$daveVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("maven.modrinth:simple-voice-chat:fabric-$svcVersion+$targetMinecraft")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.withType<JavaCompile>().configureEach { options.release.set(targetJava); options.encoding = "UTF-8" }
val modMetadata = mapOf(
    "version" to project.version.toString(),
    "minecraftVersion" to targetMinecraft,
    "javaVersion" to targetJava.toString(),
    "loaderVersion" to fabricLoaderVersion,
    "voicechatVersion" to svcVersion
)
tasks.processResources {
    inputs.properties(modMetadata)
    filesMatching("fabric.mod.json") { expand(modMetadata) }
}
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
