package it.smartcommunitylab.minionotificationsmanager.common;

public class NoSuchBucketException extends Exception {

    private static final long serialVersionUID = 1276724681341634705L;

    public NoSuchBucketException() {
        super("no such bucket");
    }

    public NoSuchBucketException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchBucketException(String message) {
        super(message);
    }

    public NoSuchBucketException(Throwable cause) {
        super(cause);
    }
}
