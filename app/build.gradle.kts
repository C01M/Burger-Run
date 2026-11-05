plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.c01m.burgerrun"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.c01m.burgerrun"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

// libGDX ships its native libraries inside jars; unpack each ABI where AGP looks for JNI libs.
val gdxAbis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val gdxNatives = configurations.create("gdxNatives")
dependencies {
    implementation(libs.gdx.core)
    implementation(libs.gdx.backend.android)
    gdxAbis.forEach { abi ->
        gdxNatives("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-$abi")
    }
}
val copyGdxNatives = tasks.register<Copy>("copyGdxNatives") {
    gdxAbis.forEach { abi ->
        from(gdxNatives.filter { it.name.endsWith("natives-$abi.jar") }.elements.map { files -> files.map { zipTree(it) } }) {
            include("*.so")
            into(abi)
        }
    }
    into(layout.projectDirectory.dir("src/main/jniLibs"))
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(copyGdxNatives) }
