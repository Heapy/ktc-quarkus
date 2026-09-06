package io.heapy.ktc.quarkus.sample

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.notNullValue
import kotlin.test.Test

@QuarkusTest
class GreetingResourceTest {
    @Test
    fun hello() {
        given()
            .`when`().get("/hello")
            .then().statusCode(200).body(notNullValue())
    }
}
