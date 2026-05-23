package de.jamsintown.user;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration-Tests für den Gruppen-Flow.
 *
 * Voraussetzungen aus import-test.sql:
 *   - User "gruppenUser" (id=2) ist Mitglied der Gruppe "TestGruppe" (id=1)
 *   - User "admin" (id=0) ist Admin
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisabledIfEnvironmentVariable(named = "SKIP_INTEGRATION_TESTS", matches = "true")
class GruppenFlowTest {

    private String expiresAt() {
        return ZonedDateTime.now().plusDays(7).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    // -------------------------------------------------------------------------
    // getSelf — Gruppen-Daten im User-Objekt
    // -------------------------------------------------------------------------

    @Test
    @TestSecurity(user = "gruppenuser", roles = {"user"})
    @JwtSecurity(claims = {@Claim(key = "upn", value = "gruppenuser")})
    void getSelf_userInGruppe_gibtGruppenZurueck() {
        given()
            .when().get("/api/v1/users/self")
            .then()
            .statusCode(200)
            .body("groups.size()", equalTo(1))
            .body("groups[0].name", equalTo("TestGruppe"));
    }

    @Test
    @TestSecurity(user = "gruppenuser", roles = {"user"})
    @JwtSecurity(claims = {@Claim(key = "upn", value = "gruppenuser")})
    void getSelf_aktiveGruppeInitialNull() {
        given()
            .when().get("/api/v1/users/self")
            .then()
            .statusCode(200)
            .body("activeGroup", nullValue());
    }

    // -------------------------------------------------------------------------
    // setActiveGroup / clearActiveGroup
    // -------------------------------------------------------------------------

    @Test
    @org.junit.jupiter.api.Order(10)
    @TestSecurity(user = "gruppenuser", roles = {"user"})
    @JwtSecurity(claims = {@Claim(key = "upn", value = "gruppenuser")})
    void setActiveGroup_alsMitglied_setzt_aktiveGruppe() {
        given()
            .when().put("/api/v1/users/self/active-group/1")
            .then()
            .statusCode(200)
            .body("activeGroup.name", equalTo("TestGruppe"));
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @TestSecurity(user = "gruppenuser", roles = {"user"})
    @JwtSecurity(claims = {@Claim(key = "upn", value = "gruppenuser")})
    void clearActiveGroup_setzt_auf_null() {
        // Erst setzen
        given().when().put("/api/v1/users/self/active-group/1").then().statusCode(200);
        // Dann löschen
        given()
            .when().delete("/api/v1/users/self/active-group")
            .then()
            .statusCode(200)
            .body("activeGroup", nullValue());
    }

    @Test
    @TestSecurity(user = "gruppenuser", roles = {"user"})
    @JwtSecurity(claims = {@Claim(key = "upn", value = "gruppenuser")})
    void setActiveGroup_nichtExistierendeGruppe_gibt404() {
        given()
            .when().put("/api/v1/users/self/active-group/9999")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "user"})
    @JwtSecurity(claims = {@Claim(key = "upn", value = "admin")})
    void setActiveGroup_alsNichtMitglied_gibt403() {
        // Admin ist kein Mitglied von TestGruppe (id=1)
        given()
            .when().put("/api/v1/users/self/active-group/1")
            .then()
            .statusCode(403);
    }

    // -------------------------------------------------------------------------
    // Einladung mit Label → Gruppe wird angelegt
    // -------------------------------------------------------------------------

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "user"})
    @JwtSecurity(claims = {@Claim(key = "upn", value = "admin")})
    void erstelleInvitationMitLabel_gruppeWirdAngelegt() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "label": "NeueTestGruppe",
                  "role": "user",
                  "expiresAt": "%s"
                }
                """.formatted(expiresAt()))
            .when().post("/api/v1/users/invitations")
            .then()
            .statusCode(201)
            .body("label", equalTo("NeueTestGruppe"))
            .body("group.name", equalTo("NeueTestGruppe"));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "user"})
    @JwtSecurity(claims = {@Claim(key = "upn", value = "admin")})
    void erstelleInvitationOhneLabel_gruppeBleibtNull() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "role": "user",
                  "expiresAt": "%s"
                }
                """.formatted(expiresAt()))
            .when().post("/api/v1/users/invitations")
            .then()
            .statusCode(201)
            .body("group", nullValue());
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "user"})
    @JwtSecurity(claims = {@Claim(key = "upn", value = "admin")})
    void erstelleZweiInvitationsMitGleihemLabel_gleicheGruppe() {
        String body = """
            {
              "label": "SharedGruppe",
              "role": "user",
              "expiresAt": "%s"
            }
            """.formatted(expiresAt());

        given()
            .contentType(ContentType.JSON).body(body)
            .when().post("/api/v1/users/invitations")
            .then().statusCode(201);

        // Zweiter Token für dieselbe Gruppe ist nicht erlaubt (1 Token pro Gruppe)
        given()
            .contentType(ContentType.JSON).body(body)
            .when().post("/api/v1/users/invitations")
            .then().statusCode(409);
    }
}
