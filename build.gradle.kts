plugins {
    id("java-library")
    id("maven-publish")
}

val cloud = "1.6.2"

group = "live.mcparty"
version = cloud

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    api("cloud.commandframework:cloud-core:$cloud")
    compileOnly("com.github.Minestom:Minestom:db2d00819c")
}

tasks.withType(JavaCompile::class) {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    repositories {
        maven {
            url = uri("https://repo.mcparty.live/packages/")
            credentials {
                username = project.findProperty("mcp.user") as? String ?: System.getenv("REPO_USERNAME")
                password = project.findProperty("mcp.key") as? String ?: System.getenv("REPO_TOKEN")
            }
        }
    }

    publications {
        create<MavenPublication>("mcp") {
            from(components["java"])
        }
    }
}
