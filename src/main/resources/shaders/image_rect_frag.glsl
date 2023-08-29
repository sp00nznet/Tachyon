#version 330 core

in vec2 varying_uv;
in vec4 varying_colour;

out vec4 colour;

uniform sampler2D tex;

void main() {
    colour = texture2D(tex, varying_uv) * varying_colour;
}
