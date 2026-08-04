pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ProjectEllysia/AcheronCore es privado y GitHub Packages no permite
        // descargas anonimas ni siquiera de paquetes publicos: hace falta un PAT
        // con read:packages (+ repo, por ser repo privado). Ponlo en
        // ~/.gradle/gradle.properties como gpr.user / gpr.token.
        maven {
            name = "AcheronCore"
            url = uri("https://maven.pkg.github.com/ProjectEllysia/AcheronCore")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
            content { includeGroup("com.ellysia") }
        }
    }
}

rootProject.name = "AcheronMobile"
include(":app")
