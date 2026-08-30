import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.03.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.vibetgram.core:core-api:0.1.0-SNAPSHOT")
    implementation("org.vibetgram.core:core-storage:0.1.0-SNAPSHOT")
    implementation("org.vibetgram.core:core-tdlib:0.1.0-SNAPSHOT")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

val supportedNativeAbis = listOf("arm64-v8a", "x86_64")
val generatedJniLibs = layout.buildDirectory.dir("generated/jniLibs")
val telegramApiId = providers.gradleProperty("telegramApiId")
    .orElse(providers.environmentVariable("VIBETGRAM_TELEGRAM_API_ID"))
    .orElse("0")
val telegramApiHash = providers.gradleProperty("telegramApiHash")
    .orElse(providers.environmentVariable("VIBETGRAM_TELEGRAM_API_HASH"))
    .orElse("")
val telegramApiHashValue = telegramApiHash.get()
val telegramApiHashPattern = Regex("^[0-9a-f]{32}$")
check(telegramApiHashValue.isEmpty() || telegramApiHashPattern.matches(telegramApiHashValue)) {
    "telegramApiHash must be a 32-character lowercase hexadecimal value"
}

fun buildConfigString(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}
val stageTdlibNative by tasks.registering(Sync::class) {
    supportedNativeAbis.forEach { abi ->
        from(rootProject.file("tdlib/prebuilt/$abi/lib")) {
            include("libtdjsonjava.so")
            into(abi)
        }
    }
    into(generatedJniLibs)
}

android {
    namespace = "org.vibetgram.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "org.vibetgram.client"
        minSdk = 30
        targetSdk = 36
        versionCode = providers.gradleProperty("vibetgramVersionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("vibetgramVersionName").orElse("0.1.0").get()
        manifestPlaceholders["vibetgramChannel"] = "stable"
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId.get().toInt().toString())
        buildConfigField("String", "TELEGRAM_API_HASH", buildConfigString(telegramApiHashValue))
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".nightly"
            manifestPlaceholders["vibetgramChannel"] = "nightly"
        }
        getByName("release") {
            isMinifyEnabled = false
            manifestPlaceholders["vibetgramChannel"] = "stable"
        }
        create("development") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            matchingFallbacks += listOf("debug")
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["vibetgramChannel"] = "preview"
        }
        create("internal") {
            // Internal output is release-like but intentionally unsigned.
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            matchingFallbacks += listOf("release")
            isDebuggable = true
            manifestPlaceholders["vibetgramChannel"] = "nightly"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets {
        getByName("main") {
            kotlin.directories.addAll(
                listOf(
                    "../gui/src/main/kotlin",
                    "../gui/src/androidMain/kotlin",
                ),
            )
            jniLibs.directories.add(generatedJniLibs.get().asFile.absolutePath)
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(stageTdlibNative, "validateNativeLibraries")
}

tasks.register("validateNativeLibraries") {
    group = "verification"
    dependsOn(stageTdlibNative)
    doLast {
        supportedNativeAbis.forEach { abi ->
            val library = generatedJniLibs.get().file("$abi/libtdjsonjava.so").asFile
            check(library.isFile && library.length() > 0) {
                "Pinned TDLib native library missing for $abi: ${library.absolutePath}"
            }
        }
    }
}

tasks.register("validateInternalArchive") {
    group = "verification"
    description = "Validate the internal APK archive and reject signing metadata."
    dependsOn("assembleInternal")
    doLast {
        val apks = fileTree(layout.buildDirectory.dir("outputs/apk/internal"))
            .matching { include("*-unsigned.apk") }
            .files
        check(apks.size == 1) { "Expected one unsigned internal APK, found ${apks.size}" }
        val apk = apks.single()
        ZipFile(apk).use { archive ->
            check(archive.getEntry("AndroidManifest.xml") != null) { "APK has no manifest" }
            check(archive.getEntry("classes.dex") != null) { "APK has no DEX" }
            check(archive.entries().asSequence().none { entry ->
                val name = entry.name.uppercase()
                name.startsWith("META-INF/") &&
                    (name == "META-INF/MANIFEST.MF" ||
                        name.endsWith(".SF") || name.endsWith(".RSA") ||
                        name.endsWith(".DSA") || name.endsWith(".EC") || name.endsWith(".SIG"))
            }) { "Internal APK contains signing metadata" }
        }
    }
}
