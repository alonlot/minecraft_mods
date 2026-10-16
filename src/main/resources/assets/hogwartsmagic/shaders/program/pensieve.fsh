#version 150

// The Pensieve memory grade. Darkness drives the build from shadow: 1 is black, around 0.6 the world shows as
// soft grey shadow shapes, 0 is the real (silvered) memory.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float Time;
uniform float Desaturation;
uniform float Darkness;
uniform vec3 Tint;
uniform float Vignette;
uniform float RedEdge;

out vec4 fragColor;

void main() {
    vec3 sharp = texture(DiffuseSampler, texCoord).rgb;

    // Shapes come out of the dark soft and sharpen: a small blur that fades as the darkness lifts.
    float soft = clamp(Darkness * 1.6, 0.0, 1.0);
    vec3 col = sharp;
    if (soft > 0.01) {
        vec2 r = oneTexel * (1.0 + 3.0 * soft);
        vec3 blur = sharp * 4.0;
        blur += texture(DiffuseSampler, texCoord + vec2( r.x, 0.0)).rgb * 2.0;
        blur += texture(DiffuseSampler, texCoord + vec2(-r.x, 0.0)).rgb * 2.0;
        blur += texture(DiffuseSampler, texCoord + vec2(0.0,  r.y)).rgb * 2.0;
        blur += texture(DiffuseSampler, texCoord + vec2(0.0, -r.y)).rgb * 2.0;
        blur += texture(DiffuseSampler, texCoord + r).rgb;
        blur += texture(DiffuseSampler, texCoord - r).rgb;
        blur += texture(DiffuseSampler, texCoord + vec2(r.x, -r.y)).rgb;
        blur += texture(DiffuseSampler, texCoord + vec2(-r.x, r.y)).rgb;
        col = mix(sharp, blur / 16.0, soft);
    }

    // The silvery memory grade: washed out, cool, shadows lifted a touch toward blue.
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(luma), Desaturation);
    col *= Tint;
    col += vec3(0.015, 0.02, 0.035) * Desaturation * (1.0 - luma);
    // A faint shimmer, so the memory never looks quite still.
    col *= 1.0 + 0.012 * sin(Time * 6.2831853 + texCoord.y * 40.0);

    // Shadow shapes, then black.
    vec3 shadow = vec3(luma * 0.32) * vec3(0.85, 0.9, 1.0);
    col = mix(col, shadow, smoothstep(0.0, 0.6, Darkness));
    col *= 1.0 - smoothstep(0.6, 1.0, Darkness);

    // Vignette, and the red flicker at the edges when the body left at the basin is struck.
    vec2 d = texCoord - 0.5;
    d.x *= InSize.x / max(InSize.y, 1.0);
    float edge = smoothstep(0.35, 0.95, length(d) * 1.15);
    col *= 1.0 - edge * Vignette;
    col = mix(col, vec3(0.55, 0.03, 0.03), clamp(edge * RedEdge * 0.85, 0.0, 1.0));

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
