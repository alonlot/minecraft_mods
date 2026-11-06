#version 150

// Heat haze: the frame (SceneSampler, a copy) bent by rising, wavering noise wherever the haze mask says the air is
// hot. The mask is the glow buffer's alpha, blurred down to a quarter (MaskSampler) and an eighth (WideSampler) of
// the frame; it is also looked up from below, so the shimmer climbs well above a flame and hardly hangs under it.
// Outside the mask nothing is written at all.

uniform sampler2D SceneSampler;
uniform sampler2D MaskSampler;
uniform sampler2D WideSampler;
uniform float Time;
uniform float Aspect;

in vec2 texCoord;

out vec4 fragColor;

// How far the air bends at full heat, as a fraction of the screen height (about 4 pixels at 1080p).
const float BEND = 0.0038;
const float MASK_GAIN = 1.6;

void main() {
    float here = texture(MaskSampler, texCoord).a;
    float above = texture(WideSampler, texCoord - vec2(0.0, 0.04)).a;
    float high = texture(WideSampler, texCoord - vec2(0.0, 0.09)).a;
    float m = max(here, max(above * 0.85, high * 0.55));
    m = clamp(m * MASK_GAIN, 0.0, 1.0);
    m = m * m * (3.0 - 2.0 * m);
    if (m < 0.004) {
        discard;
    }
    // Two octaves of crossed waves that travel upward, aspect-corrected so the cells stay round.
    vec2 p = vec2(texCoord.x * Aspect, texCoord.y) * 34.0;
    float t = Time;
    float nx = sin(p.y * 1.7 - t * 6.3 + sin(p.x * 0.9 + t * 1.9) * 1.6)
             + 0.5 * sin(p.y * 3.7 - t * 9.1 + p.x * 1.3);
    float ny = sin(p.x * 1.3 + t * 2.7 + sin(p.y * 1.1 - t * 4.3) * 1.4)
             + 0.5 * sin(p.x * 3.3 - p.y * 2.1 - t * 7.7);
    vec2 offset = vec2(nx / Aspect, ny) * (BEND * m / 1.5);
    vec2 uv = clamp(texCoord + offset, vec2(0.0), vec2(1.0));
    fragColor = vec4(texture(SceneSampler, uv).rgb, 1.0);
}
