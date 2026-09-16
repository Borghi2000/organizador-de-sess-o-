pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "estoque-chocolate"

include(":app")

// O core e um build separado de proposito: ele nao depende do Android e por isso pode ser
// compilado e testado sozinho, com "cd core && gradle test", sem SDK do Android instalado.
includeBuild("core")
