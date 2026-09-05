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
        // usingsky/KoreanLunarCalendar (MIT) 배포용 — 음력 생일 지원에 사용.
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "k-home-note-android"
include(":app")
