rootProject.name = "AmongUs"

includeBuild("../GenesiCore") {
    dependencySubstitution {
        substitute(module("dev.genesi:games-api")).using(project(":games-api"))
    }
}
