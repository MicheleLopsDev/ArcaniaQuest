plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serializzaz)
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
    implementation(libs.kotlinx.serializzazione.json)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Le regole non sanno leggere file: il catalogo glielo passano i test.
    systemProperty("arcaniaquest.catalogo", rootProject.file("content/moduli/catalogo.json").absolutePath)
    testLogging { events("passed", "failed", "skipped") }
}
