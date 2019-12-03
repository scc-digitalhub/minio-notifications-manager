package it.smartcommunitylab.minionotificationsmanager.minio;

public class MinioException extends Exception {

    private static final long serialVersionUID = -4592233514577258727L;

    public MinioException() {
        super();
    }

    public MinioException(String message, Throwable cause) {
        super(message, cause);
    }

    public MinioException(String message) {
        super(message);
    }

    public MinioException(Throwable cause) {
        super(cause);
    }
}
