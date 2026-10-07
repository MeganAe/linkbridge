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

// Version lue par la boîte « À propos » : un fichier de propriétés écrit à la compilation,
// pour que l'application n'ait rien à télécharger et rien à deviner.
val generatedResourcesDir = layout.buildDirectory.dir("generated/linkbridge-resources")

val generateLinkBridgeProperties by tasks.registering {
    val outputDir = generatedResourcesDir
    val versionValue = appVersion
    inputs.property("appVersion", versionValue)
    outputs.dir(outputDir)
    doLast {
        val directory = outputDir.get().asFile
        directory.mkdirs()
        File(directory, "linkbridge.properties")
            .writeText("version=$versionValue\n", Charsets.UTF_8)
    }
}

sourceSets.main {
    resources.srcDir(generateLinkBridgeProperties)
}

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
            // Propriétés du fichier .exe : société et copyright visibles dans l'explorateur.
            vendor = "Metoushela Walker"
            // Chaîne volontairement sans caractère non ASCII : jpackage lit son fichier
            // d'arguments avec l'encodage de la plateforme, et un « © » y casse la commande
            // sous Linux et donne un texte illisible dans les propriétés du .exe sous Windows.
            copyright = "Copyright 2026 Metoushela Walker"

            windows {
                // Version explicite au niveau Windows ET MSI : évite la dérivation
                // qui produisait '0' (MSI exige MAJOR.MINOR.BUILD).
                packageVersion = appVersion
                msiPackageVersion = appVersion
                menu = true
                menuGroup = "LinkBridge"
                shortcut = true
                dirChooser = true
                // Installation par utilisateur, sans droits administrateur.
                perUserInstall = true
                // Identifiant d'installation stable : les mises à jour remplacent la version
                // précédente au lieu de créer une seconde entrée dans la liste des programmes.
                upgradeUuid = "9C6C71B2-3B2E-45B6-9C67-7E4B0A1D8F52"
                val icon = rootProject.file("installer/linkbridge.ico")
                if (icon.exists()) {
                    iconFile.set(icon)
                }
            }
        }
    }
}
