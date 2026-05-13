plugins {
    application
    java
}

repositories {
    mavenCentral()
}

sourceSets.main.get().java.srcDir("src")
sourceSets.test.get().java.srcDir("test")

dependencies {
    implementation("org.ow2.asm:asm:9.7.1")

    testImplementation("junit:junit:4.13.2")
    implementation("junit:junit:4.13.2")
    implementation("com.google.guava:guava:31.1-jre")
}

application {
    mainClass.set("compiler.Compiler")
}
