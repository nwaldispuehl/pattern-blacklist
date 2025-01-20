package ch.retorte.blacklist;

/**
 * How do we classify a given number?
 */
public enum Classification {
    /** Should be blocked. */
    SPAM,
    /** Can be trusted. */
    HAM,
    /** Could not be matched to either SPAM or HAM. */
    UNKNOWN
}
