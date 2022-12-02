#extension GL_OES_EGL_image_external : require

attribute vec3 a_Position;
attribute vec3 a_FragColor;
uniform mat4 u_Model;
uniform vec3 u_Translation;

varying vec3 v_FragColor;

void main() {
    float x = -(a_Position.x + u_Translation.x);
    float y = a_Position.y + u_Translation.y;
    float z = a_Position.z + u_Translation.z;

    gl_PointSize = 10.0;
    //gl_Position = vec4(a_Position, 1.0) * u_Model;
    gl_Position = vec4(x * 8.0, y * 6.0, z, 1.0) * u_Model;
    v_FragColor = a_FragColor;
}