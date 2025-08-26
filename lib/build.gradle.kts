import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.vanniktech.publish)
    signing
}

version = "2.0.0"

android {
    namespace = "com.appliedrec.verid3.facetemplateregistry"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            testCoverage {
                enableAndroidTestCoverage = true
            }
        }
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
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(libs.verid.common)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(kotlin("test"))
}

mavenPublishing {
    coordinates("com.appliedrec", "face-template-registry")
    pom {
        name.set("Face Template Registry")
        description.set("Register, authenticate and identify face templates")
        url.set("https://github.com/AppliedRecognition/Face-Template-Registry-Android")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/AppliedRecognition/Face-Template-Registry-Android.git")
            developerConnection.set("scm:git:ssh://github.com/AppliedRecognition/Face-Template-Registry-Android.git")
            url.set("https://github.com/jakubdolejs/Face-Template-Registry-Android")
        }
        developers {
            developer {
                id.set("appliedrecognition")
                name.set("Applied Recognition Corp.")
                email.set("support@appliedrecognition.com")
            }
        }
    }
    publishToMavenCentral(automaticRelease = true)
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}