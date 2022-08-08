package it.unibo.sd.beccacino;

public enum ResponseCode {
    OK(200, "Ok"),
    LEAVE(201, "Player left lobby"),
    JOIN(202, "Player joined lobby"),
    CREATE_ERROR(400, "Cannot create lobby."),
    LEAVE_ERROR(401, "Cannot leave lobby."),
    JOIN_ERROR(402, "Cannot join lobby."),
    START_ERROR(403, "Cannot start the match."),
    PERMISSION_DENIED(405, "Permission denied."),
    ILLEGAL_REQUEST(406, "Illegal request"),
    FAIL(407, "Operation failed");

    private final int code;
    private final String message;

    ResponseCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return this.code;
    }

    public String getMessage() {
        return this.message;
    }

    @Override
    public String toString() {
        return this.message;
    }
}
