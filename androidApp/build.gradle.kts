import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val productionAdmobAppId = providers.gradleProperty("ADMOB_ANDROID_APP_ID")
    .orElse("")
val productionInterstitialId = providers.gradleProperty("ADMOB_ANDROID_INTERSTITIAL_ID")
    .orElse(providers.environmentVariable("ADMOB_ANDROID_INTERSTITIAL_ID"))
    .orElse("")
val appIdPattern = Regex("ca-app-pub-[0-9]{16}~[0-9]{10}")
val unitIdPattern = Regex("ca-app-pub-[0-9]{16}/[0-9]{10}")

val validateReleaseAds by tasks.registering {
    group = "verification"
    description = "Require a real Android interstitial ID before packaging a release."
    inputs.property("appId", productionAdmobAppId)
    inputs.property("interstitialId", productionInterstitialId)
    doLast {
        val appId = productionAdmobAppId.get()
        val unitId = productionInterstitialId.get()
        check(appIdPattern.matches(appId) && !appId.startsWith("ca-app-pub-3940256099942544")) {
            "Set ADMOB_ANDROID_APP_ID to the production Android AdMob app ID."
        }
        check(unitIdPattern.matches(unitId) && !unitId.startsWith("ca-app-pub-3940256099942544") &&
            unitId != "ca-app-pub-9151364381169959/3382059031") {
            "Set ADMOB_ANDROID_INTERSTITIAL_ID to a production Android INTERSTITIAL unit ID " +
                "in Gradle properties or the environment. The old rewarded unit cannot be used."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateReleaseAds) }

android {
    namespace   = "com.bloxtrix.hexdrop"
    compileSdk  = 36
    defaultConfig {
        applicationId = "com.bloxtrix.hexdrop"
        minSdk        = 26
        targetSdk     = 36
        versionCode   = 1
        versionName   = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val api = providers.gradleProperty("ILMERYA_API_URL").orElse("").get()
        val gamesId = providers.gradleProperty("ILMERYA_PGS_APP_ID").orElse("").get()
        val gamesClient = providers.gradleProperty("ILMERYA_PGS_WEB_CLIENT_ID").orElse("").get()
        require(api.isBlank() || (api.startsWith("https://") && api.matches(Regex("https://[a-zA-Z0-9./_-]+"))))
        require(gamesId.isBlank() || gamesId.all(Char::isDigit))
        require(gamesClient.isBlank() || gamesClient.matches(Regex("[a-zA-Z0-9.-]+")))
        buildConfigField("String", "COMPETITION_URL", "\"$api\"")
        buildConfigField("String", "PGS_WEB_CLIENT_ID", "\"$gamesClient\"")
        buildConfigField("boolean", "PGS_CONFIGURED", (gamesId.isNotBlank() && gamesClient.isNotBlank()).toString())
        resValue("string", "games_app_id", gamesId.ifBlank { "0" })
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
        release {
            manifestPlaceholders["admobAppId"] = productionAdmobAppId.get()
            val validatedId = productionInterstitialId.get().takeIf(unitIdPattern::matches).orEmpty()
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$validatedId\"")
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Release code path (R8, resources shrunk) with Google test ads and the debug key, for device QA only.
        create("qa") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.play.games)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.8")
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
