#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 beamData;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    // x is side opacity; y is longitudinal taper. Both retain float precision.
    beamData = UV0;
}
