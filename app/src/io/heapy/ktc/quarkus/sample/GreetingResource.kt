package io.heapy.ktc.quarkus.sample

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/hello")
open class GreetingResource {
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    fun hello(): String = "Hello from Quarkus on the Kotlin Toolchain"
}
