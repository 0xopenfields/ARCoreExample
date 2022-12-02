#extension GL_OES_EGL_image_external : require

varying vec3 v_FragColor;

void main() {
    //gl_FragColor = vec4(v_FragColor, 0.0);
    gl_FragColor = vec4(1.0, 1.0, 1.0, 0.0);
}