#version 150

in vec4 vertexColor;
in vec2 beamData;

out vec4 fragColor;

void main() {
    // Emitted color is independent of the world's lightmap and fog.
    fragColor = vec4(vertexColor.rgb, beamData.x * beamData.y);
}
