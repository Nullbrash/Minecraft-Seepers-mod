#version 150

in vec4 vertexColor;
in float texel;

out vec4 fragColor;

void main() {
    if (vertexColor.a <= 0.0) discard;
    // A texel (analysis III) is a hard square of its pixel's colour: neighbours must not blur into each other.
    // Blending takes the brighter of point and screen (GL_MAX ignores alpha), so the colour carries it.
    if (texel > 0.5) {
        fragColor = vec4(vertexColor.rgb * vertexColor.a, vertexColor.a);
        return;
    }
    vec2 corner = gl_PointCoord * 2.0 - 1.0;
    float d = dot(corner, corner);
    if (d > 1.0) discard;
    // Soft edge: a glowing dot rather than a hard disc.
    float alpha = vertexColor.a * (1.0 - smoothstep(0.3, 1.0, d));
    fragColor = vec4(vertexColor.rgb * alpha, alpha);
}
