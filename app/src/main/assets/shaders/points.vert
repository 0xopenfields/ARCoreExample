#version 400

layout(location = 0) in vec4 vertex_position;
layout(location = 1) in vec4 vertex_color;
uniform mat4 view;
uniform mat4 model;
uniform mat4 projection;
out vec4 color;

void main() {
    color = vertex_color;
    gl_Position = projection * view * model * vertex_position;
    gl_PointSize = 3.0;
}