plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Shared by the Android app and the desktop server: the wire format and the merge rules,
// so the two sides can never disagree about what "newest wins" means.
// Java 17 bytecode, built with whichever JDK (17 or newer) runs Gradle: the PC has 17,
// the Mac 21 and 23.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // api: both sides speak these types, so they need the serializer too.
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
