#version 120
varying vec2 guiPosition;
uniform vec4 Rect;
void main() {
    guiPosition=Rect.xy-4.0+gl_Vertex.xy*(Rect.zw+8.0);
    gl_Position=gl_ModelViewProjectionMatrix*vec4(guiPosition,0.0,1.0);
}
