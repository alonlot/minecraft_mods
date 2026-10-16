package com.hogwartsmagic.pensieve.scene;

/** A scene file problem, naming the JSON path it was found at. */
public final class SceneException extends Exception {
    private final String path;

    public SceneException(String path, String message) {
        super(path + ": " + message);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
