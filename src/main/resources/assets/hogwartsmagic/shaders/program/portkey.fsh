#version 150

// The Portkey's tunnel. One pass over the finished level (before the hand and the HUD).
//
// The world part wrings the frame round the tunnel's centre: swirled (Twist, hardest in the middle), pulled in
// towards the middle (Pull; negative pushes out as the far end opens), smeared towards the middle (Blur, 8 taps)
// and split into red and blue fringes along the radius (Chroma, in pixels), with a cold light lifting the edges
// (Glow). The tunnel part is a spinning well of streaks: three bands of lanes (24, 48 and 96 round the circle),
// a few lit in each, dashes racing along them towards the viewer, coloured mostly blue-white with gold, cyan and the
// rare rose and violet; the walls a deep blue-black stirred by two octaves of noise flowing past; the vanishing point
// burning white; the rim streaks whiter and longer, the rim itself darkening. Mix blends the world into the tunnel.
//
// Integer (PCG) hashing throughout, so there is no sin-hash banding, and the angle wraps without a seam.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform vec2 InSize;
uniform float PkTime;
uniform float Mix;
uniform float Twist;
uniform float Pull;
uniform float Blur;
uniform float Chroma;
uniform float Spin;
uniform float CentreX;
uniform float CentreY;
uniform float Glow;
uniform float Seed;

out vec4 fragColor;

const float TAU = 6.28318530718;
// Distance from the centre (in short-side units) treated as the edge of the view.
const float EDGE = 0.72;

const vec3 CORE = vec3(0.84, 0.93, 1.00);
const vec3 GLOW = vec3(0.75, 0.88, 1.00);
const vec3 GOLD = vec3(1.00, 0.85, 0.54);
const vec3 CYAN = vec3(0.56, 0.89, 1.00);
const vec3 ROSE = vec3(1.00, 0.70, 0.78);
const vec3 VIOLET = vec3(0.71, 0.61, 1.00);
const vec3 WALL_DEEP = vec3(0.043, 0.039, 0.118);  // #0B0A1E
const vec3 WALL_OUT = vec3(0.122, 0.165, 0.333);   // #1F2A55
const vec3 NEB_A = vec3(0.10, 0.06, 0.22);
const vec3 NEB_B = vec3(0.05, 0.14, 0.25);
// Share of lanes lit in each band, how long a dash is (in its cycle), and the depth rings' spacing.
const float LIT = 0.80;
const float DASH = 0.26;
const float DASH_GAIN = 0.2;
const float RING_FREQ = 0.35;

uint pcg(uint v) {
    uint state = v * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

float hashU(uint v) {
    return float(pcg(v)) * (1.0 / 4294967295.0);
}

float hash2(uint a, uint b) {
    return hashU(a ^ pcg(b + 0x9E3779B9u));
}

// Value noise on a lattice that wraps every `period` cells in x (the angle), so the tunnel has no seam.
float vnoise(vec2 p, float period, uint salt) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float x0 = mod(i.x, period);
    float x1 = mod(i.x + 1.0, period);
    uint y0 = uint(int(i.y) + 1048576);
    uint y1 = y0 + 1u;
    float a = hash2(uint(x0) + salt, y0);
    float b = hash2(uint(x1) + salt, y0);
    float c = hash2(uint(x0) + salt, y1);
    float d = hash2(uint(x1) + salt, y1);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

vec3 palette(float h) {
    if (h < 0.70) return CORE;
    if (h < 0.85) return GOLD;
    if (h < 0.94) return CYAN;
    if (h < 0.97) return ROSE;
    return VIOLET;
}

vec3 world(vec2 centre, vec2 aspect) {
    vec2 d = (texCoord - centre) * aspect;
    float r = length(d);
    float rn = r / EDGE;
    float a = atan(d.y, d.x);
    a += Twist * (1.0 - smoothstep(0.0, 1.0, rn));
    r *= 1.0 - Pull * (1.0 - clamp(rn, 0.0, 1.0));
    vec2 dir = vec2(cos(a), sin(a));
    vec2 uv = centre + dir * r / aspect;

    vec3 col;
    if (Blur > 0.001) {
        // Eight taps smeared towards the centre: speed.
        col = vec3(0.0);
        float total = 0.0;
        for (int i = 0; i < 8; i++) {
            float k = float(i) / 7.0;
            float w = 1.0 - 0.6 * k;
            col += texture(DiffuseSampler, mix(uv, centre, Blur * 0.14 * k * clamp(rn, 0.0, 1.0))).rgb * w;
            total += w;
        }
        col /= total;
    } else {
        col = texture(DiffuseSampler, uv).rgb;
    }
    if (Chroma > 0.05) {
        // Red pushed out along the radius, blue pulled in: the frame tearing at its edges.
        vec2 off = dir / aspect * (Chroma / InSize.y) * clamp(rn * 1.3, 0.0, 1.0);
        float amount = clamp(Chroma, 0.0, 1.0);
        col.r = mix(col.r, texture(DiffuseSampler, uv + off).r, amount);
        col.b = mix(col.b, texture(DiffuseSampler, uv - off).b, amount);
    }
    col += Glow * rn * rn * GLOW * 0.6;
    return col;
}

vec3 tunnel(vec2 centre, vec2 aspect) {
    vec2 d = (texCoord - centre) * aspect;
    float r = length(d);
    float rn = r / EDGE;
    float a = atan(d.y, d.x);
    float z = 0.35 / max(r, 0.02);
    float u = fract(a / TAU + Spin / TAU + 0.08 * z);
    float v = z + PkTime * 2.2;
    uint seed = uint(int(Seed));

    // The walls: deep blue-black, stirred by noise flowing past.
    float n = vnoise(vec2(u * 12.0, v * 0.7), 12.0, seed) * 0.65 + vnoise(vec2(u * 24.0, v * 1.6), 24.0, seed + 77u) * 0.35;
    vec3 col = mix(WALL_DEEP, WALL_OUT, smoothstep(0.0, 1.1, rn)) * (0.4 + 1.2 * n);
    // Coloured nebula drifting through the walls, violet to deep cyan.
    float neb = vnoise(vec2(u * 6.0, v * 0.35), 6.0, seed + 5u);
    col += mix(NEB_A, NEB_B, n) * smoothstep(0.4, 0.9, neb) * 1.8;
    // Faint rings racing towards the viewer: the tunnel's depth.
    col += GLOW * pow(0.5 + 0.5 * cos(v * TAU * RING_FREQ), 16.0) * 0.10 * smoothstep(0.15, 0.6, rn);

    // The streaks: three bands of lanes, a few lit in each.
    float rim = smoothstep(0.8, 1.15, rn);
    for (int band = 0; band < 3; band++) {
        float lanes = 24.0 * exp2(float(band));
        float cell = floor(u * lanes);
        uint id = uint(int(cell)) + uint(band) * 4099u + seed * 131u;
        float lit = hashU(id);
        if (lit > LIT) {
            float across = abs(fract(u * lanes) - 0.5) * 2.0;
            float width = 0.55 - 0.12 * float(band);
            float line = 1.0 - smoothstep(0.0, width, across);
            float speed = 0.7 + 1.3 * hash2(id, 3u);
            float phase = hash2(id, 7u) * 10.0;
            float seg = fract((v * speed + phase) * 0.45);
            float len = (DASH + DASH_GAIN * min(z * 0.25, 1.0)) * (1.0 + 1.2 * rim);
            float dash = smoothstep(0.0, 0.04, seg) * (1.0 - smoothstep(len * 0.35, len, seg));
            vec3 tint = mix(palette(hash2(id, 13u)), vec3(1.0), rim * 0.6);
            float bright = (1.6 - 0.35 * float(band)) * (0.6 + 0.4 * hash2(id, 19u));
            col += tint * line * dash * bright;
        }
    }

    // The vanishing point burns white; the rim darkens.
    col += CORE * exp(-6.0 * rn) * (0.9 + 0.6 * Glow);
    col *= 1.0 - 0.55 * smoothstep(0.95, 1.5, rn);
    return col;
}

void main() {
    vec2 aspect = vec2(InSize.x / max(InSize.y, 1.0), 1.0);
    vec2 centre = vec2(CentreX, CentreY);
    vec3 col = world(centre, aspect);
    if (Mix > 0.002) {
        col = mix(col, tunnel(centre, aspect), clamp(Mix, 0.0, 1.0));
    }
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
