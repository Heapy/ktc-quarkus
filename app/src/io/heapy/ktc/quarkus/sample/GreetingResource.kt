package io.heapy.ktc.quarkus.sample

import io.heapy.ktc.quarkus.sample.greeting.Greeter
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/hello")
class GreetingResource(
    private val greeter: Greeter,
) {
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    fun hello(): String = greeter.greet()
}
