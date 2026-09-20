plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.json:json:20240303")
    testImplementation(libs.junit)
}
