#version 150

// The colour draining out of the world at a big moment. Amount 0 is the world as it is, 1 is grey with a lifted
// contrast and a dark vignette closing in.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform vec2 InSize;
uniform float Amount;

out vec4 fragColor;

void main() {
    vec3 col = texture(DiffuseSampler, texCoord).rgb;
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(luma), Amount);
    // A slight contrast lift around mid grey, so the grey world still reads as sharp.
    col = (col - 0.5) * (1.0 + 0.25 * Amount) + 0.5;
    vec2 d = texCoord - 0.5;
    d.x *= InSize.x / max(InSize.y, 1.0);
    float edge = smoothstep(0.3, 0.95, length(d) * 1.15);
    col *= 1.0 - edge * 0.55 * Amount;
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
