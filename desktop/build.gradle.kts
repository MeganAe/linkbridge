import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// Version de l'application, format jpackage/MSI : MAJOR.MINOR.BUILD (ex. 0.3.0).
// Forçable avec -PappVersion=0.3.0 depuis la CI.
val appVersion: String = providers.gradleProperty("appVersion")
    .getOrElse("0.3.0")
    .trim()
    .removePrefix("v")
    .substringBefore("-")
    .let { if (Regex("""\d+\.\d+\.\d+""").matches(it)) it else "0.3.0" }

// La version du projet sert de repli à plusieurs endroits de la DSL jpackage.
version = appVersion

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
            // L'installateur .exe est produit par Inno Setup (installer/linkbridge.iss),
            // pas par jpackage : on ne demande que le format MSI à jpackage.
            targetFormats(TargetFormat.Msi)
            packageName = "LinkBridge"
            packageVersion = appVersion
            description = "Un pont, pas un hotspot."
            copyright = "© 2026 Metoushela Walker"
            vendor = "LinkBridge"

            windows {
                // Version explicite au niveau Windows ET MSI : évite la dérivation
                // qui produisait '0' (MSI exige MAJOR.MINOR.BUILD).
                packageVersion = appVersion
                msiPackageVersion = appVersion
                menu = true
                shortcut = true
                dirChooser = true
                // Installation par utilisateur, sans droits administrateur.
                perUserInstall = true
                val icon = rootProject.file("installer/linkbridge.ico")
                if (icon.exists()) {
                    iconFile.set(icon)
                }
            }
        }
    }
}
