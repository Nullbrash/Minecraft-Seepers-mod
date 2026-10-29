#version 150

// One vertex per point, drawn as a sprite: the fragment shader makes it a disc. Position is relative to the section
// origin (ChunkOffset = origin - camera), in 1/1024 block.
in vec3 Position;
in vec4 Color;  // alpha: how well light shows the surface (0 dark .. 1 lit); its lowest bit flags a texel
in float Born;  // seconds
in float Life;  // seconds; 0 = never fades

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform vec2 ScreenSize;
uniform float PointTime;
uniform float FogStart;
uniform float FogEnd;
uniform float PointSize;
uniform float MinPixels;

out vec4 vertexColor;
// 1: a texel (analysis III) — the fragment shader draws a hard square.
out float texel;

// Cheap per-point noise in [0, 1).
float hash(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

void main() {
    vec3 local = Position / 1024.0;
    bool broken = Life > 65534.5;
    texel = mod(floor(Color.a * 255.0 + 0.5), 2.0);
    if (broken) {
        // Broken data (anomalous rock): the point jumps around its place a few times a second.
        float step = floor(PointTime * 7.0);
        local += (vec3(hash(local + step), hash(local.yzx + step), hash(local.zxy + step)) - 0.5) * 0.18;
    }
    vec4 view = ModelViewMat * vec4(local + ChunkOffset, 1.0);
    float depth = max(-view.z, 0.05);
    // A surface is seen if it is lit AND the fog lets it through; the point fills in what is not seen.
    // The fog itself never hides the point (CLAUDE.md 5).
    float fog = clamp((length(view.xyz) - FogStart) / max(FogEnd - FogStart, 0.001), 0.0, 1.0);
    float seen = Color.a * (1.0 - fog);
    gl_Position = ProjMat * view;
    // Fixed world size, never smaller than MinPixels: far points stay visible.
    gl_PointSize = max(PointSize * ProjMat[1][1] * ScreenSize.y / (2.0 * depth), MinPixels);
    // Texels exactly a cell wide leave rows of 1-pixel gaps after rounding (dark moire lines): they overlap a little
    // instead, which the max blending does not brighten.
    if (texel > 0.5) gl_PointSize = gl_PointSize * 1.15 + 1.0;

    // Full brightness for the first two thirds of the lifetime, then a linear fade.
    float fade = 1.0;
    if (broken) {
        fade = 0.35 + 0.65 * hash(Position + floor(PointTime * 11.0));
    } else if (Life > 0.0) {
        float age = PointTime - Born;
        float fadeFrom = Life * (2.0 / 3.0);
        fade = 1.0 - clamp((age - fadeFrom) / max(Life - fadeFrom, 0.001), 0.0, 1.0);
    }
    vertexColor = vec4(Color.rgb, (1.0 - seen) * fade);
}
