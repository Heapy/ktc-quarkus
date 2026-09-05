package io.heapy.ktc.quarkus.sample.greeting

import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class Greeter {
    fun greet(): String = "Hello from a local Kotlin Toolchain module"
}
