#version 150

// The finished glow over the frame. A four-tap tent smooths the half-size level up to full size, 1 - e^-x eases it
// so a blazing core rolls off instead of clipping, and the blend (screen: dst + src * (1 - dst)) lifts the dark
// round the light far more than it washes out what is already bright.

uniform sampler2D DiffuseSampler;
uniform vec2 InTexel;
uniform float Strength;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 d = InTexel * 0.5;
    vec3 b = texture(DiffuseSampler, texCoord + vec2(-d.x, -d.y)).rgb;
    b += texture(DiffuseSampler, texCoord + vec2( d.x, -d.y)).rgb;
    b += texture(DiffuseSampler, texCoord + vec2(-d.x,  d.y)).rgb;
    b += texture(DiffuseSampler, texCoord + vec2( d.x,  d.y)).rgb;
    b = max(b * 0.25, vec3(0.0));
    fragColor = vec4(1.0 - exp(-b * Strength), 1.0);
}
