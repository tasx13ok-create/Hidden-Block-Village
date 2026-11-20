#version 120
// Steam Dash impact frame: the scene becomes inked silhouettes on hot paper, thick outlines from a
// two-texel Sobel, and a red rim where the ink meets the paper.
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
    float edge = clamp(length(vec2(gx, gy)) * 2.2, 0.0, 1.0);
    float ink = 1.0 - smoothstep(0.30, 0.36, c);          // dark areas become solid ink
    float line = smoothstep(0.35, 0.6, edge);
    vec3 paper = vec3(1.0, 0.95, 0.88);
    vec3 rim = vec3(0.85, 0.12, 0.05);
    vec3 col = mix(paper, rim, smoothstep(0.15, 0.35, edge) * (1.0 - ink));
    col = mix(col, vec3(0.03, 0.02, 0.02), max(ink, line));
    gl_FragColor = vec4(col, 1.0);
}
