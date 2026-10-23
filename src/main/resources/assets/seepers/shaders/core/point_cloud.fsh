#version 150

in vec4 vertexColor;
in vec2 corner;

out vec4 fragColor;

void main() {
    float d = dot(corner, corner);
    if (d > 1.0 || vertexColor.a <= 0.0) discard;
    // Soft edge: a glowing dot rather than a hard disc.
    fragColor = vec4(vertexColor.rgb, vertexColor.a * (1.0 - smoothstep(0.3, 1.0, d)));
}
