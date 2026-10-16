package com.hogwartsmagic.pensieve.scene;

/**
 * One camera key. {@code lookAt} is a point, or null when {@code lookAtActor} names an actor to follow.
 * {@code ease} shapes the segment ending here; {@code cut} makes that segment a hard jump instead.
 */
public record CameraKey(float t, float[] pos, float[] lookAt, String lookAtActor, float fov, float roll,
                        Ease ease, boolean cut) {
}
