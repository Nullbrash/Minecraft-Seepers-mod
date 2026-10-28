#version 150

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 corner = gl_PointCoord * 2.0 - 1.0;
    float d = dot(corner, corner);
    if (d > 1.0 || vertexColor.a <= 0.0) discard;
    // Soft edge: a glowing dot rather than a hard disc.
    fragColor = vec4(vertexColor.rgb, vertexColor.a * (1.0 - smoothstep(0.3, 1.0, d)));
}
