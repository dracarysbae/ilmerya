plugins { kotlin("jvm") version "2.3.10"; application }
kotlin { jvmToolchain(21) }
sourceSets {
    main {
        kotlin.srcDir("../shared/src/commonMain/kotlin")
        kotlin.include("com/ilmerya/server/**", "com/bloxtrix/hexdrop/engine/**", "com/bloxtrix/hexdrop/model/**", "com/bloxtrix/hexdrop/competition/RunReplay.kt")
    }
}
dependencies {
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.json:json:20240303")
    implementation("org.slf4j:slf4j-nop:2.0.13")
    testImplementation(kotlin("test"))
}
application { mainClass.set("com.ilmerya.server.ServerKt") }
tasks.test { useJUnit() }
