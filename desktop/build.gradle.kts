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
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

compose.desktop {
    application {
        mainClass = "com.preform.desktop.MainKt"
        nativeDistributions {
            // Windows: a folder with "Preform Library.exe" and its own runtime (an installer
            // needs the WiX toolset). macOS: a signed, notarized .dmg, built on a Mac.
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Preform Library"
            packageVersion = "1.0.0"
            description = "Organizes your book library and shares it with Preform on your phone."
            vendor = "Preform"
            copyright = "Preform"
            includeAllModules = true
            windows {
                menuGroup = "Preform"
                upgradeUuid = "6d4c2a8e-4f0b-4c63-9a6e-2b5f1d7e3c90"
                iconFile.set(project.file("icons/icon.ico"))
            }
            macOS {
                bundleID = "com.preform.library"
                appCategory = "public.app-category.productivity"
                dockName = "Preform Library"
                iconFile.set(project.file("icons/icon.icns"))
                minimumSystemVersion = "12.0"
                entitlementsFile.set(project.file("macos/entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("macos/entitlements.plist"))
                infoPlist {
                    extraKeysRawXml = """
                        <!-- Lives in the menu bar only: no Dock icon. -->
                        <key>LSUIElement</key>
                        <true/>
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>Preform Library shares your books with the Preform app on your phones over your home network.</string>
                        <key>NSBonjourServices</key>
                        <array>
                            <string>_preform._tcp</string>
                        </array>
                    """.trimIndent()
                }
                // Signed with the Developer ID certificate in the Mac's keychain when
                // macos/build.sh names it; notarized there too, with a keychain profile.
                // Nothing secret lives in the project.
                val identity = System.getenv("PREFORM_SIGN_IDENTITY")
                if (!identity.isNullOrBlank()) {
                    signing {
                        sign.set(true)
                        this.identity.set(identity)
                    }
                }
            }
        }
    }
}

tasks.test {
    // For WindowScreenshots: -Dscreenshots.data=<a data folder to render from>.
    systemProperty("screenshots.data", System.getProperty("screenshots.data") ?: "")
    // For IconMaker: -Dicons.out=<folder> (resolved from the project root).
    systemProperty("icons.out", System.getProperty("icons.out")?.let { rootProject.file(it).path } ?: "")
}
