package ch.retorte.blacklist;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ch.retorte.blacklist.Classification.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link Classifier}.
 */
@QuarkusTest
public class ClassifierTest {

    // ---- Statics

    static List<String> patterns = new ArrayList<>();


    // ---- Fields

    @Inject
    Classifier classifier;


    // ---- Test methods

    @Test
    void shouldClassifyHamAndSpam() {
        // Given
        patterns = usePatterns(
                "[spam]",
                "123",
                "5*",
                "[ham]",
                "9NN");
        classifier.reloadPatternsFromFile();

        // When/Then
        isSpam(classifier.classify("123"));
        isUndecided(classifier.classify("1234"));
        isUndecided(classifier.classify("124"));
        isUndecided(classifier.classify("12"));
        isUndecided(classifier.classify("0123"));

        isSpam(classifier.classify("5"));
        isSpam(classifier.classify("55"));
        isSpam(classifier.classify("5555555555"));
        isSpam(classifier.classify("5123"));
        isUndecided(classifier.classify("65555"));

        isHam(classifier.classify("911"));
        isHam(classifier.classify("955"));
        isUndecided(classifier.classify("9"));
        isUndecided(classifier.classify("91"));
        isUndecided(classifier.classify("9111"));
    }

    @Test
    void shouldProcessDifferentOrder() {
        // Given
        patterns = usePatterns(
                "[ham]",
                "123",
                "[spam]",
                "456");
        classifier.reloadPatternsFromFile();

        // When/Then
        isHam(classifier.classify("123"));
        isSpam(classifier.classify("456"));
        isUndecided(classifier.classify("789"));
    }

    @Test
    void shouldPrioritizeHamIfNumberMatchesBoth() {
        // Given
        patterns = usePatterns(
                "[spam]",
                "123",
                "[ham]",
                "123");
        classifier.reloadPatternsFromFile();

        // When/Then
        isHam(classifier.classify("123"));
    }

    @Test
    void shouldTreatUnmarkedEntriesAsSpam() {
        // Given
        patterns = usePatterns("123");
        classifier.reloadPatternsFromFile();

        // When/Then
        isSpam(classifier.classify("123"));
    }

    @Test
    void shouldIgnoreNonPatternCharacters() {
        // Given
        patterns = usePatterns("1-2.3 N myFriend");
        classifier.reloadPatternsFromFile();

        // When/Then
        isSpam(classifier.classify("1234"));
    }


    // ---- Helper methods

    private List<String> usePatterns(String... s) {
        return Arrays.asList(s);
    }

    private void isHam(Classification classification) {
        assertSame(HAM, classification);
    }

    private void isSpam(Classification classification) {
        assertSame(SPAM, classification);
    }

    private void isUndecided(Classification classification) {
        assertSame(UNKNOWN, classification);
    }


    // ---- Inner classes

    @Alternative()
    @Priority(1)
    @ApplicationScoped
    private static class MockClassifier extends Classifier {

        @Override
        protected List<String> obtainPatternFileContents() {
            return patterns;
        }
    }

}
