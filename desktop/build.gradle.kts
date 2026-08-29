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

/**
 * Perlustra un mucchio di sotterranei e dice se si tengono tutti.
 *
 * Non apre finestre: monta il dungeon, lo cammina un passo alla volta
 * come farebbe il gruppo, e controlla di essere arrivato dappertutto.
 * Fallisce se anche uno solo si interrompe, quindi si puo' mettere in
 * una verifica automatica.
 *
 *   ./gradlew perlustra
 *   ./gradlew perlustra -Psemi=1-500 -Ppezzi=16
 *   ./gradlew perlustra -Psemi=8BD -Pdiario=si
 */
tasks.register<JavaExec>("perlustra") {
    group = "verification"
    description = "Cammina i sotterranei generati e verifica che siano percorribili per intero"
    mainClass.set("dev.michelelops.arcaniaquest.desktop.PerlustraKt")
    classpath = sourceSets["main"].runtimeClasspath
    // gli asset stanno in content/, come per :desktop:run
    workingDir = rootProject.file("content")

    val semi = providers.gradleProperty("semi")
    val pezzi = providers.gradleProperty("pezzi")
    val diario = providers.gradleProperty("diario")
    argumentProviders.add {
        buildList {
            semi.orNull?.let { add("--semi=$it") }
            pezzi.orNull?.let { add("--pezzi=$it") }
            diario.orNull?.let { add("--diario=$it") }
        }
    }
}
