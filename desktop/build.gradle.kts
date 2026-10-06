import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// Version embarquée dans l'installateur (jpackage exige x.y.z). Elle peut être
// forcée par -PappVersion=0.3.0, par exemple depuis le workflow de release.
val appVersion = providers.gradleProperty("appVersion").getOrElse("0.3.0")

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(project(":core"))
}

compose.desktop {
    application {
        mainClass = "com.linkbridge.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LinkBridge"
            packageVersion = appVersion
            description = "Un pont, pas un hotspot."
            copyright = "© 2026 Metoushela Walker"
            vendor = "LinkBridge"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                // Installation par utilisateur, sans droits administrateur.
                perUserInstall = true
                iconFile.set(rootProject.file("installer/linkbridge.ico"))
            }
        }
    }
}
