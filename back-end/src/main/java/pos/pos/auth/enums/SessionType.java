package pos.pos.auth.enums;

public enum SessionType {
    PASSWORD,
    // Signed in with a PIN on one of the restaurant's connected tills (doesn't count toward the 5 devices).
    PIN
}
