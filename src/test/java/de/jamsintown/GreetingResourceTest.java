package de.jamsintown;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

// @QuarkusTest
class GreetingResourceTest {
    @ConfigProperty(name = "publisher.name")
    String publisherName;
   // @Test
    void testHelloEndpoint() {
        given()
          .when().get("/hello")
          .then()
             .statusCode(200)
             .body(is("Hello from Quarkus " + publisherName + "!"));
    }

}