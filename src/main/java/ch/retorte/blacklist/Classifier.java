package ch.retorte.blacklist;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static ch.retorte.blacklist.Classification.*;
import static java.lang.String.format;

/**
 * Ingests the config file ('pattern file') and rates provided strings as HAM, SPAM, or UNKNOWN.
 * Monitors the pattern file for change, and reloads it accordingly.
 * The pattern file should have this structure (one entry per line) with these markers:
 * <pre>
 * [spam]
 * SPAM_PATTERN_1
 * SPAM_PATTERN_2
 * ...
 * [ham]
 * HAM_PATTERN_1
 * ...
 * </pre>
 * If neither the SPAM (`[spam]`) nor the HAM (`[ham]`) marker are present all entries are treated as SPAM patterns.
 */
@Singleton
public class Classifier {

    // ---- Statics

    private static final String SPAM_SECTION_IDENTIFIER = "[spam]";
    private static final String HAM_SECTION_IDENTIFIER = "[ham]";


    // ---- Injects

    @Inject
    Logger log;


    // ---- Fields

    @ConfigProperty(name = "pattern.file.path")
    String patternFilePath;

    private final List<String> spamInputPatterns = new ArrayList<>();
    private final List<String> spamRegexPatterns = new ArrayList<>();
    private final List<String> hamInputPatterns = new ArrayList<>();
    private final List<String> hamRegexPatterns = new ArrayList<>();

    private boolean running = true;
    private Thread fileWatcherThread;


    // ---- Methods

    void onStart(@Observes StartupEvent event) {
        log.debug("Starting with blacklist file path: " + patternFilePath);
        reloadPatternsFromFile();
        addFileWatcher();
    }

    void onStop(@Observes ShutdownEvent event) {
        running = false;
        stopFileWatcher();
    }

    public synchronized Classification classify(String number) {
        if (hamRegexPatterns.stream().anyMatch(number::matches)) {
            return HAM;
        }
        else if (spamRegexPatterns.stream().anyMatch(number::matches)) {
            return SPAM;
        }
        else {
            return UNKNOWN;
        }
    }

    public List<String> getPatterns() {
        final List<String> patterns = new ArrayList<>();
        if (!spamInputPatterns.isEmpty()) {
            patterns.add(SPAM_SECTION_IDENTIFIER);
            patterns.addAll(spamInputPatterns);
        }
        if (!hamInputPatterns.isEmpty()) {
            patterns.add(HAM_SECTION_IDENTIFIER);
            patterns.addAll(hamInputPatterns);
        }
        return patterns;
    }

    synchronized void reloadPatternsFromFile() {
        hamInputPatterns.clear();
        hamRegexPatterns.clear();
        spamInputPatterns.clear();
        spamRegexPatterns.clear();

        List<String> lines = obtainPatternFileContents();
        boolean spamPattern = true;
        for (String line : lines) {
            final String cleanedLine = line != null ? line.trim() : "";
            if (cleanedLine.startsWith("#") || cleanedLine.isEmpty()) {
                continue;
            }

            if (cleanedLine.startsWith(SPAM_SECTION_IDENTIFIER)) {
                spamPattern = true;
                continue;
            }

            if (cleanedLine.startsWith(HAM_SECTION_IDENTIFIER)) {
                spamPattern = false;
                continue;
            }

            final String normalizedLine = normalizePattern(cleanedLine);

            if (normalizedLine.isEmpty()) {
                continue;
            }

            log.debug(format("Ingesting %s pattern: %s", spamPattern ? SPAM : HAM, cleanedLine));

            if (spamPattern) {
                spamInputPatterns.add(cleanedLine);
                spamRegexPatterns.add(convertToRegex(normalizedLine));
            }
            else {
                hamInputPatterns.add(cleanedLine);
                hamRegexPatterns.add(convertToRegex(normalizedLine));
            }
        }
    }

    private String normalizePattern(String s) {
        // We remove everything except numbers, the 'N', and the '*' character.
        return s.replaceAll("[^0-9N*]", "");
    }

    protected List<String> obtainPatternFileContents() {
        try {
            return Files.readAllLines(patternFile(), StandardCharsets.UTF_8 );
        } catch (IOException e) {
            log.error(format("Pattern file '%s' could not be read due to: %s",
                    patternFilePath, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
        return new ArrayList<>();
    }

    private Path patternFile() {
        return Paths.get(patternFilePath);
    }

    private String convertToRegex(String pattern) {
        return pattern
                .replace("*", ".*")
                .replace("N", ".");
    }

    private void addFileWatcher() {
        final Path path = patternFile();

        if (!path.toFile().exists()) {
            log.error("Unable to attach watcher to pattern file. Skipping file watching.");
            return;
        }

        fileWatcherThread = new Thread(() -> {

            long lastModified = 0;
            try {
                lastModified = Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                log.warn(format("Unable to determine last modified time for pattern file '%s' due to: %s",
                        patternFilePath, e.getMessage()));
            }

            while (running) {
                try {
                    long currentModified = Files.getLastModifiedTime(path).toMillis();
                    if (currentModified != lastModified) {
                        log.info(format("File change in pattern file '%s' detected, reloading.", path));
                        reloadPatternsFromFile();

                        lastModified = currentModified;
                    }
                    TimeUnit.SECONDS.sleep(5);
                }
                catch (IOException e) {
                    log.warn("File watcher failed due to: " + e.getMessage());
                }
                catch (InterruptedException e) {
                    // nop
                }
            }
        });
        fileWatcherThread.start();
    }

    private void stopFileWatcher() {
        try {
            if (fileWatcherThread != null) {
                fileWatcherThread.interrupt();
            }
        } catch (SecurityException e) {
            // nop
        }
    }
}
