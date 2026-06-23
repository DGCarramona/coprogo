package tech.justdev

import io.micronaut.runtime.Micronaut

fun main(args: Array<String>) {
    Micronaut
        .build(*args)
        .defaultEnvironments("runtime", "local")
        .mainClass(Application::class.java)
        .start()
}

private object Application
