package it.smartcommunitylab.minionotificationsmanager.common;

public class NoSuchEventException extends Exception {

    private static final long serialVersionUID = 3529824197668192091L;

    public NoSuchEventException() {
        super("no such event");
    }

    public NoSuchEventException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchEventException(String message) {
        super(message);
    }

    public NoSuchEventException(Throwable cause) {
        super(cause);
    }
}
