#version 120
// Lariat impact frame: the scene as a black-and-white negative. Bright sky and ground go to ink black,
// dark shapes and every edge burn to white, so the two fighters read as bright silhouettes for a beat.
uniform sampler2D DiffuseSampler;
varying vec2 texCoord;
varying vec2 oneTexel;

float lum(vec2 uv) { return dot(texture2D(DiffuseSampler, uv).rgb, vec3(0.299, 0.587, 0.114)); }

void main() {
    vec2 o = oneTexel * 2.0;
    float tl = lum(texCoord + vec2(-o.x, -o.y)), t = lum(texCoord + vec2(0.0, -o.y)), tr = lum(texCoord + vec2(o.x, -o.y));
    float l = lum(texCoord + vec2(-o.x, 0.0)), c = lum(texCoord), r = lum(texCoord + vec2(o.x, 0.0));
    float bl = lum(texCoord + vec2(-o.x, o.y)), b = lum(texCoord + vec2(0.0, o.y)), br = lum(texCoord + vec2(o.x, o.y));
    float gx = (tr + 2.0 * r + br) - (tl + 2.0 * l + bl);
    float gy = (bl + 2.0 * b + br) - (tl + 2.0 * t + tr);
    float edge = smoothstep(0.45, 0.8, clamp(length(vec2(gx, gy)) * 2.2, 0.0, 1.0));
    float neg = 1.0 - smoothstep(0.28, 0.42, c);        // dark becomes white, bright becomes black
    float v = max(neg, edge);
    vec3 col = mix(vec3(0.0, 0.0, 0.012), vec3(1.0), v);
    gl_FragColor = vec4(col, 1.0);
}
