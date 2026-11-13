package pos.pos.mail;

public enum EmailOutboxStatus {
    /** Waiting to be sent (first try or a retry). */
    PENDING,
    /** A server is sending it right now; if that server dies, it goes back to the queue when the lease runs out. */
    SENDING,
    SENT,
    /** Given up: the mail server refused it, the retries ran out or its link expired first. */
    FAILED,
    /** A newer email replaced it before it went out (e.g. the invitation was sent again). */
    CANCELLED
}
