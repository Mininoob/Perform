import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":sync-protocol"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.auto.head)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.tls.certificates)

    implementation(libs.pdfbox)
    implementation(libs.sqlite.jdbc)
    implementation(libs.jmdns)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.okhttp)
}

compose.desktop {
    application {
        mainClass = "com.preform.desktop.MainKt"
        nativeDistributions {
            // A folder with "Preform Library.exe" and its own runtime. An installer
            // (Exe/Msi) needs the WiX toolset, which isn't installed yet.
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Preform Library"
            packageVersion = "1.0.0"
            description = "Shares your book library with Preform on your phone."
            vendor = "Preform"
            includeAllModules = true
            windows {
                menuGroup = "Preform"
                upgradeUuid = "6d4c2a8e-4f0b-4c63-9a6e-2b5f1d7e3c90"
            }
        }
    }
}
