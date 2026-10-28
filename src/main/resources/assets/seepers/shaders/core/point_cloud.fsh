#version 150

in vec4 vertexColor;
in float texel;

out vec4 fragColor;

void main() {
    if (vertexColor.a <= 0.0) discard;
    // A texel (analysis III) is a hard square of its pixel's colour: neighbours must not blur into each other.
    if (texel > 0.5) {
        fragColor = vertexColor;
        return;
    }
    vec2 corner = gl_PointCoord * 2.0 - 1.0;
    float d = dot(corner, corner);
    if (d > 1.0) discard;
    // Soft edge: a glowing dot rather than a hard disc.
    fragColor = vec4(vertexColor.rgb, vertexColor.a * (1.0 - smoothstep(0.3, 1.0, d)));
}
