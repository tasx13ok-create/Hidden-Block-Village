#version 120
uniform sampler2D Scene;
uniform vec4 Rect;
uniform float Radius;
uniform float Opacity;
uniform float Cut;
uniform vec3 Tint;
uniform vec2 BlurStep;
uniform vec2 Screen;
uniform vec2 Origin;
varying vec2 guiPosition;
void main() {
    vec2 q=abs(guiPosition-Rect.xy-Rect.zw*.5)-Rect.zw*.5+Radius;
    float d=length(max(q,0.0))+min(max(q.x,q.y),0.0)-Radius;
    if(Cut>0.0)d=max(d,(guiPosition.x+guiPosition.y-Rect.x-Rect.y-Rect.z-Rect.w+Cut)*.70710678);
    float coverage=1.0-smoothstep(-.35,.35,d);
    float shadow=exp(-max(d,0.0)*.95)*.19*(1.0-coverage);
    if(coverage<.001) { gl_FragColor=vec4(.015,.025,.04,shadow*Opacity);return; }
    vec2 uv=(gl_FragCoord.xy-Origin)/Screen;
    vec3 scene=vec3(0.0);
    // A small normalized 5x5 Gaussian, sampled only inside visible panels.
    for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++) {
        float wx=x==0?6.0:((x==1||x==-1)?4.0:1.0);
        float wy=y==0?6.0:((y==1||y==-1)?4.0:1.0);
        scene+=texture2D(Scene,uv+vec2(float(x),float(y))*BlurStep).rgb*wx*wy/256.0;
    }
    float luma=dot(scene,vec3(.2126,.7152,.0722));
    scene=mix(vec3(luma),scene,.68);
    float vertical=clamp((guiPosition.y-Rect.y)/Rect.w,0.0,1.0);
    vec3 glass=mix(scene,vec3(.07,.078,.09)+Tint*.018,.60);
    glass+=vec3(.043,.049,.056)*pow(1.0-vertical,3.0);
    float rim=1.0-smoothstep(-1.0,-.1,d);
    glass=mix(glass,vec3(.81,.89,.96),rim*mix(.055,.23,1.0-vertical));
    float material=.91*coverage;
    gl_FragColor=vec4(glass,(material+shadow)*Opacity);
}
