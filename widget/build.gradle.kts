import java.util.Properties

plugins { id("com.android.application") }

val releaseSigningFile = rootProject.file("signing/release.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}

android {
    namespace = "me.elizarov.weather"
    compileSdk = 36
    defaultConfig {
        applicationId = "me.elizarov.weather"
        minSdk = 30
        targetSdk = 30
        versionCode = 3
        versionName = "0.2.1"
        testInstrumentationRunner = "me.elizarov.weather.WidgetSmoke"
    }
    sourceSets.getByName("main").res.srcDir("../local-assets/generated/widget-res")
    sourceSets.getByName("debug").res.srcDir("../local-assets/res")
    buildFeatures { buildConfig = true }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("signing").resolve(releaseSigning.getProperty("storeFile", "release.keystore"))
            storeType = releaseSigning.getProperty("storeType", "PKCS12")
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    testBuildType = providers.gradleProperty("testBuildType").getOrElse("debug")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { disable += "ExpiredTargetSdkVersion" }
}

val verifyLocalOemResources by tasks.registering {
    doLast {
        val generated = rootProject.file("local-assets/generated")
        for (path in listOf("manifest.json", "widget-res/layout/weather.xml",
                "widget-res/drawable/appwidget_data_divider.xml", "widget-res/drawable/appwidget_data_divider_light.xml",
                "widget-res/values/fonts.xml")) {
            if (!generated.resolve(path).isFile) {
                throw GradleException("Missing local OEM resources. Run python3 tools/pull-assets.py, then ./tools/build.sh (see README).")
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyLocalOemResources) }

val verifyReleaseSigning by tasks.registering {
    doLast {
        if (!releaseSigningFile.isFile || listOf("storeFile", "storeType", "storePassword", "keyAlias", "keyPassword")
                .any { releaseSigning.getProperty(it).isNullOrBlank() }) {
            throw GradleException("Release signing is not configured. Run python3 tools/signing.py init.")
        }
        if (!rootProject.file("signing").resolve(releaseSigning.getProperty("storeFile")).isFile) {
            throw GradleException("Release keystore is missing. Restore signing/ from your backup.")
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" || it.name == "preReleaseAndroidTestBuild" }
    .configureEach { dependsOn(verifyReleaseSigning) }
