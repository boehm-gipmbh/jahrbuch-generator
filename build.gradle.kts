plugins {
    java
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-hibernate-reactive-panache")
    implementation("io.quarkus:quarkus-resteasy-reactive-jackson:3.15.4")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-hibernate-reactive")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-reactive-pg-client")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-elytron-security-common")
    implementation("io.quarkus:quarkus-smallrye-jwt")
    implementation("io.quarkus:quarkus-smallrye-jwt-build")
    implementation("org.gphoto:gphoto2-java:1.5-SNAPSHOT")
    implementation("io.quarkiverse.quinoa:quarkus-quinoa:2.5.5")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")

}

group = "de.jamsintown"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

//tasks.withType<io.quarkus.gradle.tasks.QuarkusBuild> {
//    systemProperty("quarkus.native.builder-image.java-home-env-var", "JAVA_HOME")
//    jvmArgs("-Xmx8g", "-XX:MaxMetaspaceSize=2g")
//}


//// build.gradle.kts
//tasks.register<Exec>("npmBuild") {
//    workingDir = file("src/main/frontend")
//    commandLine("${System.getProperty("user.home")}/.nvm/versions/node/v20.17.0/bin/npm", "run", "build")
//}
//
//tasks.register<Copy>("copyFrontend") {
//    dependsOn("npmBuild")
//    from("src/main/frontend/build")
//    into("build/classes/frontend")
//}
//
//tasks.processResources {
//    dependsOn("copyFrontend")
//}