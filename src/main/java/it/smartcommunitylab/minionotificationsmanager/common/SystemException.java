package it.smartcommunitylab.minionotificationsmanager.common;

public class SystemException extends Exception {

    private static final long serialVersionUID = -849830499955329388L;

    public SystemException() {
        super();
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }

    public SystemException(String message) {
        super(message);
    }

    public SystemException(Throwable cause) {
        super(cause);
    }
}
