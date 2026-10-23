#version 150

// Camera-facing disc around a point. Position is relative to the section origin (ChunkOffset = origin - camera).
in vec3 Position;
in vec4 Color;
in vec2 UV0;   // x: born, y: lifetime (seconds)
in ivec2 UV1;  // disc corner, -1 or 1

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform vec2 ScreenSize;
uniform float PointTime;
uniform float PointSize;
uniform float MinPixels;

out vec4 vertexColor;
out vec2 corner;

void main() {
    vec4 view = ModelViewMat * vec4(Position + ChunkOffset, 1.0);
    float depth = max(-view.z, 0.05);
    // World size that covers MinPixels at this depth: far points stay visible.
    float minSize = MinPixels * 2.0 * depth / (ProjMat[1][1] * ScreenSize.y);
    float radius = max(PointSize, minSize) * 0.5;
    view.xy += vec2(UV1) * radius;
    gl_Position = ProjMat * view;

    // Full brightness for the first two thirds of the lifetime, then a linear fade.
    float age = PointTime - UV0.x;
    float fadeFrom = UV0.y * (2.0 / 3.0);
    float fade = 1.0 - clamp((age - fadeFrom) / max(UV0.y - fadeFrom, 0.001), 0.0, 1.0);
    vertexColor = vec4(Color.rgb, Color.a * fade);
    corner = vec2(UV1);
}
