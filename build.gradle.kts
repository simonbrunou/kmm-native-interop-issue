plugins { kotlin("multiplatform") version "1.6.21" }

group = "com.bystamp"

version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

kotlin {
    mingwX64("native") {
        binaries { executable { entryPoint = "main" } }
        val main by compilations.getting
        val bluetooth by main.cinterops.creating { includeDirs { allHeaders("include") } }
    }

    sourceSets {
        val nativeMain by getting
        val nativeTest by getting
    }
}
