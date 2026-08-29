plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":gioco"))
    implementation(libs.gdx.backend.lwjgl3)
    // I binari nativi: sono gli stessi per Windows, Linux e macOS.
    runtimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
}

application {
    mainClass.set("dev.michelelops.arcaniaquest.desktop.ScrivaniaKt")
}

// Gdx.files.internal parte dalla cartella di lavoro: content/ e' la
// stessa radice che su Android e' la cartella degli asset.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("content")
}
