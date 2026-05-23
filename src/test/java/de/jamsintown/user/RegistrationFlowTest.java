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
import static org.hamcrest.Matchers.*;

/**
 * End-to-End-Tests für den Registrierungs-Flow.
 *
 * Quarkus startet einmalig mit einem echten PostgreSQL-Container (DevServices).
 * Flyway-Migrationen laufen durch, import-test.sql legt den Admin-User an.
 *
 * Testablauf:
 *   1. Admin erstellt einen Einladungstoken
 *   2. Token wird validiert
 *   3. User registriert sich mit dem Token → erhält JWT
 *   4. Doppelte E-Mail schlägt fehl
 *   5. Admin deaktiviert Token → Token wird abgelehnt
 *   6. Zugriffskontrolle auf Admin-Endpoints
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisabledIfEnvironmentVariable(named = "SKIP_INTEGRATION_TESTS", matches = "true")
class RegistrationFlowTest {

    // Wird zwischen Tests weitergegeben (statisch, da @QuarkusTest-Instanz pro Test)
    static String createdTokenUuid;

    private String expiresAt() {
        return ZonedDateTime.now().plusDays(7).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    // -------------------------------------------------------------------------
    // Admin: Token erstellen
    // -------------------------------------------------------------------------

    @Test
    @org.junit.jupiter.api.Order(1)
    @TestSecurity(user = "admin", roles = {"admin", "user"})
    @JwtSecurity(claims = {@Claim(key = "upn", value = "admin")})
    void admin_erstelltToken_gibt201MitUuidZurueck() {
        createdTokenUuid = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "label": "Testgruppe",
                  "role": "user",
                  "expiresAt": "%s"
                }
                """.formatted(expiresAt()))
        .when()
            .post("/api/v1/users/invitations")
        .then()
            .statusCode(201)
            .body("token", not(emptyString()))
            .body("label", equalTo("Testgruppe"))
            .body("active", equalTo(true))
        .extract()
            .path("token");
    }

    // -------------------------------------------------------------------------
    // Token validieren
    // -------------------------------------------------------------------------

    @Test
    @org.junit.jupiter.api.Order(2)
    void validateToken_gueltigesToken_gibt200() {
        given()
            .queryParam("token", createdTokenUuid)
        .when()
            .get("/api/v1/auth/validate-token")
        .then()
            .statusCode(200)
            .body("active", equalTo(true));
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void validateToken_unbekannterToken_gibt410() {
        given()
            .queryParam("token", "00000000-0000-0000-0000-000000000000")
        .when()
            .get("/api/v1/auth/validate-token")
        .then()
            .statusCode(410);
    }

    // -------------------------------------------------------------------------
    // Registrierung
    // -------------------------------------------------------------------------

    @Test
    @org.junit.jupiter.api.Order(3)
    void register_gueltigesToken_gibt201() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("token", createdTokenUuid)
            .body("""
                {
                  "name": "MaxMustermann",
                  "email": "max@test.de",
                  "password": "Sicher1!Passwort"
                }
                """)
        .when()
            .post("/api/v1/auth/register")
        .then()
            .statusCode(201);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void register_doppelteEmail_gibt409() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("token", createdTokenUuid)
            .body("""
                {
                  "name": "AnderePerson",
                  "email": "max@test.de",
                  "password": "Andere1!Passwort"
                }
                """)
        .when()
            .post("/api/v1/auth/register")
        .then()
            .statusCode(409);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void register_ungueltigerToken_gibt410() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("token", "00000000-0000-0000-0000-000000000000")
            .body("""
                {
                  "name": "Wer auch immer",
                  "email": "wer@test.de",
                  "password": "pw"
                }
                """)
        .when()
            .post("/api/v1/auth/register")
        .then()
            .statusCode(410);
    }

    // -------------------------------------------------------------------------
    // Admin: Token deaktivieren → danach abgelehnt
    // -------------------------------------------------------------------------

    @Test
    @org.junit.jupiter.api.Order(5)
    @TestSecurity(user = "admin", roles = {"admin", "user"})
    @JwtSecurity(claims = {@Claim(key = "upn", value = "admin")})
    void admin_deaktiviertToken_gibt200() {
        // Token-ID per UUID aus der Liste holen (unabhängig von anderen Tests)
        Integer tokenId = given()
        .when()
            .get("/api/v1/users/invitations")
        .then()
            .statusCode(200)
        .extract()
            .body().jsonPath().getInt("find { it.token == '" + createdTokenUuid + "' }.id");

        given()
        .when()
            .put("/api/v1/users/invitations/" + tokenId + "/deactivate")
        .then()
            .statusCode(200)
            .body("active", equalTo(false));
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void validateToken_nachDeaktivierung_gibt410() {
        given()
            .queryParam("token", createdTokenUuid)
        .when()
            .get("/api/v1/auth/validate-token")
        .then()
            .statusCode(410);
    }

    // -------------------------------------------------------------------------
    // Zugriffskontrolle
    // -------------------------------------------------------------------------

    @Test
    void invitations_ohneAuth_gibt401() {
        given()
        .when()
            .get("/api/v1/users/invitations")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "normaluser", roles = {"user"})
    @JwtSecurity(claims = {@Claim(key = "upn", value = "normaluser")})
    void invitations_alsNurUser_gibt403() {
        given()
        .when()
            .get("/api/v1/users/invitations")
        .then()
            .statusCode(403);
    }
}