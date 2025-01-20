package ch.retorte.blacklist;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusIntegrationTest
@TestProfile(PatternClassifierCheckTest.class)
public class PatternClassifierCheckTest implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("pattern.file.path", "src/test/resources/testPatterns.txt");
    }

    @Test
    void shouldReturnSpamForSpamPatterns() {
        given()
          .when().get("/check/+555-1234")
          .then()
             .statusCode(200)
             .body(is("SPAM"));
    }

    @Test
    void shouldReturnHamForHamPatterns() {
        given()
                .when().get("/check/+911")
                .then()
                .statusCode(200)
                .body(is("HAM"));
    }

    @Test
    void shouldReturnUnknownForUnknownPatterns() {
        given()
                .when().get("/check/+1234")
                .then()
                .statusCode(200)
                .body(is("UNKNOWN"));
    }
}