plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.michelelops.arcaniaquest"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.michelelops.arcaniaquest"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    sourceSets["main"].apply {
        kotlin.srcDirs("src/main/kotlin")
        // Gli asset sono gli stessi che su desktop: una sola copia,
        // una sola verita'.
        assets.srcDirs(rootProject.file("content"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/*
 * I binari nativi di libGDX arrivano dentro dei jar, uno per
 * architettura, e dentro ogni jar il .so sta in cima senza cartella.
 * Vanno quindi presi da configurazioni separate e messi ognuno nella
 * cartella della sua architettura, altrimenti si sovrascrivono.
 */
val nativiArm64: Configuration by configurations.creating
val nativiArm32: Configuration by configurations.creating
val nativiX86: Configuration by configurations.creating
val nativiX8664: Configuration by configurations.creating

dependencies {
    implementation(project(":gioco"))
    implementation(libs.gdx.backend.android)

    nativiArm64(variantOf(libs.gdx.platform) { classifier("natives-arm64-v8a") })
    nativiArm32(variantOf(libs.gdx.platform) { classifier("natives-armeabi-v7a") })
    nativiX86(variantOf(libs.gdx.platform) { classifier("natives-x86") })
    nativiX8664(variantOf(libs.gdx.platform) { classifier("natives-x86_64") })
}

val cartellaNativi = layout.buildDirectory.get().asFile.resolve("jniLibs")

fun estrai(nome: String, sorgente: Configuration, abi: String) =
    tasks.register<Copy>(nome) {
        from({ sorgente.map { zipTree(it) } }) { include("*.so") }
        into(cartellaNativi.resolve(abi))
    }

val estrazioni = listOf(
    estrai("estraiNativiArm64", nativiArm64, "arm64-v8a"),
    estrai("estraiNativiArm32", nativiArm32, "armeabi-v7a"),
    estrai("estraiNativiX86", nativiX86, "x86"),
    estrai("estraiNativiX8664", nativiX8664, "x86_64")
)

android.sourceSets["main"].jniLibs.srcDir(cartellaNativi)

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach { dependsOn(estrazioni) }
