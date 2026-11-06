#version 150

// Dual-filter upsample: an eight-tap tent from the smaller level, added (by the blend) onto the next larger one, so
// each level carries everything wider than itself. Radius is in texels of the smaller level.

uniform sampler2D DiffuseSampler;
uniform vec2 InTexel;
uniform float Radius;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 d = InTexel * Radius;
    vec3 s = texture(DiffuseSampler, texCoord + vec2(-2.0 * d.x, 0.0)).rgb;
    s += texture(DiffuseSampler, texCoord + vec2( 2.0 * d.x, 0.0)).rgb;
    s += texture(DiffuseSampler, texCoord + vec2(0.0, -2.0 * d.y)).rgb;
    s += texture(DiffuseSampler, texCoord + vec2(0.0,  2.0 * d.y)).rgb;
    s += texture(DiffuseSampler, texCoord + vec2(-d.x, -d.y)).rgb * 2.0;
    s += texture(DiffuseSampler, texCoord + vec2( d.x, -d.y)).rgb * 2.0;
    s += texture(DiffuseSampler, texCoord + vec2(-d.x,  d.y)).rgb * 2.0;
    s += texture(DiffuseSampler, texCoord + vec2( d.x,  d.y)).rgb * 2.0;
    fragColor = vec4(s / 12.0, 1.0);
}
