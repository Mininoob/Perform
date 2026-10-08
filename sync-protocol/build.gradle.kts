plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Shared by the Android app and the desktop server: the wire format and the merge rules,
// so the two sides can never disagree about what "newest wins" means.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // api: both sides speak these types, so they need the serializer too.
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
