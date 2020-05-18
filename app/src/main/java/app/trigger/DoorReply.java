package app.trigger;


// reply from door
public class DoorReply {
    public final ReplyCode code;
    public final String message;

    public enum ReplyCode {
        LOCAL_ERROR, // could establish a connection for some reason
        REMOTE_ERROR, // the door send some error
        SUCCESS, // the door send some message that has yet to be parsed
        DISABLED // Internet, WiFi or Bluetooth disabled or not supported
    }

    public DoorReply(ReplyCode code, String message) {
        this.code = code;
        this.message = message;
    }
}
