// Root: qui i plugin si dichiarano soltanto, li applicano i moduli.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serializzaz) apply false
    alias(libs.plugins.android.application) apply false
}
