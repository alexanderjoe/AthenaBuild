package dev.alexanderdiaz.athenabuild;

public interface Permissions {
    String ROOT = "athenabuild";

    String CREATE = ROOT + ".create";
    String UPLOAD = ROOT + ".upload";
    String DOWNLOAD = ROOT + ".download";
    String OPEN = ROOT + ".open";
    String CLOSE = ROOT + ".close";
    String DELETE = ROOT + ".delete";
}
