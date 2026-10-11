import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: dates, money, forecast, reminder planning and CSV encoding (SA NFR-06).
// No Android dependency, so the core rules are testable on any JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        // Compile against the Java 8 API surface. Android minSdk 26 ships the Java 8 java.time API,
        // so this flags accidental use of newer JDK APIs (e.g. LocalDate.datesUntil) at build time.
        freeCompilerArgs.add("-Xjdk-release=1.8")
    }
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
