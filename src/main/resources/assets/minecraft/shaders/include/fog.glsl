#version 150

// Hogwarts Magic override of vanilla's fog include (identical to vanilla unless asked otherwise).
//
// Vanilla fog is linear: perfectly clear up to FogStart, then a ramp to solid at FogEnd. That is exactly what a
// short render distance looks like. Real fog scatters light from right in front of you and thickens with depth,
// so when com.hogwartsmagic.client.BiomeFogClient wants real fog it sends a FogStart below -1000 (vanilla never
// goes below -8, its underwater value):
//     FogStart = -1000 - 100 * D
// where D is the optical depth reached at FogEnd. The fog is then exponential haze, 1 - exp(-D * d / FogEnd),
// joined by a soft wall over the last 30% so nothing pops in or out at the edge of the loaded world.

float hogwartsmagic_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    if (fogStart < -999.0) {
        float depth = clamp((-fogStart - 1000.0) / 100.0, 0.0, 4.0);
        float end = max(fogEnd, 1.0);
        float haze = 1.0 - exp(-depth * vertexDistance / end);
        float wall = smoothstep(end * 0.7, end, vertexDistance);
        return max(haze, wall);
    }
    if (vertexDistance <= fogStart) {
        return 0.0;
    }
    return vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
}

vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
    float fogValue = hogwartsmagic_fog_value(vertexDistance, fogStart, fogEnd);
    if (fogValue <= 0.0) {
        return inColor;
    }
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

float linear_fog_fade(float vertexDistance, float fogStart, float fogEnd) {
    return 1.0 - hogwartsmagic_fog_value(vertexDistance, fogStart, fogEnd);
}

float fog_distance(vec3 pos, int shape) {
    if (shape == 0) {
        return length(pos);
    } else {
        float distXZ = length(pos.xz);
        float distY = abs(pos.y);
        return max(distXZ, distY);
    }
}
