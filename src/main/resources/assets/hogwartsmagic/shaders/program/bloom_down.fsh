#version 150

// Dual-filter downsample: the centre and four diagonal taps one source texel out (each a bilinear 2x2), so a level
// half the size averages a 4x4 patch of the one above. Prefilter (first level only) caps stacked additive light,
// keeping its hue, and weights each tap by 1 / (1 + luma) so a single blazing pixel does not flicker as a blob.
// Alpha is the heat haze mask: a plain average.

uniform sampler2D DiffuseSampler;
uniform vec2 InTexel;
uniform float Prefilter;

in vec2 texCoord;

out vec4 fragColor;

const float MAX_BRIGHT = 2.0;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec4 tap(vec2 uv) {
    vec4 c = max(texture(DiffuseSampler, uv), vec4(0.0));
    if (Prefilter > 0.5) {
        float peak = max(c.r, max(c.g, c.b));
        c.rgb *= min(1.0, MAX_BRIGHT / max(peak, 0.0001));
        c.a = min(c.a, 4.0);
    }
    return c;
}

void main() {
    vec2 d = InTexel;
    vec4 c0 = tap(texCoord);
    vec4 c1 = tap(texCoord + vec2(-d.x, -d.y));
    vec4 c2 = tap(texCoord + vec2( d.x, -d.y));
    vec4 c3 = tap(texCoord + vec2(-d.x,  d.y));
    vec4 c4 = tap(texCoord + vec2( d.x,  d.y));
    float w0 = 4.0;
    float w1 = 1.0;
    float w2 = 1.0;
    float w3 = 1.0;
    float w4 = 1.0;
    if (Prefilter > 0.5) {
        w0 /= 1.0 + luma(c0.rgb);
        w1 /= 1.0 + luma(c1.rgb);
        w2 /= 1.0 + luma(c2.rgb);
        w3 /= 1.0 + luma(c3.rgb);
        w4 /= 1.0 + luma(c4.rgb);
    }
    vec3 rgb = (c0.rgb * w0 + c1.rgb * w1 + c2.rgb * w2 + c3.rgb * w3 + c4.rgb * w4) / (w0 + w1 + w2 + w3 + w4);
    float a = (c0.a * 4.0 + c1.a + c2.a + c3.a + c4.a) / 8.0;
    fragColor = vec4(rgb, a);
}
