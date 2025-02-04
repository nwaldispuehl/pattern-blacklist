package ch.retorte.blacklist;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import static java.lang.String.join;


@Path("/")
public class PatternBlacklistCheck {

    // ---- Injects

    @Inject
    Logger log;

    @Inject
    Classifier classifier;


    // ---- Methods

    /**
     * Yields the startup page with directions.
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String directions() {
        return """
               <html>
               <head>
                <title>Pattern Blacklist Lookup</title>
               </head>
               <body>
                <h1>Pattern Blacklist Lookup</h1>
                <p>
                    This service tells you if a given number (e.g. a phone number like <code>555-1234</code>) should be
                    blocked / treated as spam according to a pattern-based blacklist.
                    It is designed for phone numbers but can be used with any numbers.
                </p>
                <p>
                    Send the number (with or without `+` but preferably without spaces) as argument to the <code>check</code> endpoint:
                    <a href="check/+15551234">check/+15551234</a>
                </p>
                <p>
                    It either returns:
                    <ul>
                        <li><code>HAM</code> if the number is matched by a ham pattern of the blacklist, or else</li>
                        <li><code>SPAM</code> if the number is matched by a spam pattern of the blacklist, or else</li>
                        <li><code>UNKNOWN</code> if the provided number does not match any pattern.</li>
                    </ul>
                </p>
                <h2>Pattern syntax</h2>
                <p>
                    The following patterns are supported:
                    <ul>
                        <li>Numbers verbatim, e.g. <code>15551234</code> matches the number +15551234</li>
                        <li>`*`-Wildcard represents zero, one or more numbers, e.g. <code>1555*</code> matches the number +15551234</li>
                        <li>`N`-Wildcard represents exactly one number, e.g. <code>155512NN</code> matches the number +15551234</li>
                    </ul>
                    All other characters in a pattern are ignored.
                </p>
                <h2>Current Pattern List Entries</h2>
                <pre>
               %s
                </pre>
               </body>
               </html>
               """.formatted(join("\n", classifier.getPatterns()));
    }

    /**
     * Compares the provided number with the pattern blacklist and returns the verdict.
     *
     * @param number a phone number in the E.123 format, without spaces. E.g. `+15551234`, or `15551234`.
     * @return the string "SPAM" if the provided number matches the block list, "HAM" otherwise.
     */
    @GET
    @Path("check/{number}")
    @Produces(MediaType.TEXT_PLAIN)
    public String check(@PathParam("number") String number, @Context HttpServerRequest request) {
        final long startTime = System.nanoTime();

        final String normalizedNumber = normalizeNumber(number);
        final Classification result = checkBlacklistFor(normalizedNumber);

        final long totalNanoTime = System.nanoTime() - startTime;
        final double totalTime = totalNanoTime / 1_000_000.0;

        log.info(String.format("%s: Checking '%s' with verdict '%s' in %.2fms.", request.remoteAddress().host(), normalizedNumber, result, totalTime));
        return result.name();
    }

    private String normalizeNumber(String number) {
        if (isEmpty(number)) {
            return "";
        }

        // We remove anything which is not a number.
        return number.replaceAll("[^0-9]", "");
    }

    private boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private Classification checkBlacklistFor(String normalizedNumber) {
        return classifier.classify(normalizedNumber);
    }

}
