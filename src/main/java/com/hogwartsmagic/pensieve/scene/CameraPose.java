package com.hogwartsmagic.pensieve.scene;

/** A sampled camera: position, Minecraft-convention yaw/pitch (yaw 0 = +Z, pitch positive = down), roll, fov. */
public record CameraPose(double x, double y, double z, float yaw, float pitch, float roll, float fov) {
}
