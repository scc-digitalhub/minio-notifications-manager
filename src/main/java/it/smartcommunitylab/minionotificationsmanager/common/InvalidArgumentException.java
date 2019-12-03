package it.smartcommunitylab.minionotificationsmanager.common;

public class InvalidArgumentException extends Exception {

    private static final long serialVersionUID = 8986638259055828224L;

    public InvalidArgumentException() {
        super("invalid argument");
    }

    public InvalidArgumentException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidArgumentException(String message) {
        super(message);
    }

    public InvalidArgumentException(Throwable cause) {
        super(cause);
    }
}
