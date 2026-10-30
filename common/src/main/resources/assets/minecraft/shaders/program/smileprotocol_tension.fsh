#version 150

// Tension (README 21): aberracion cromatica, distorsion, desaturacion, grano y vineta que crecen
// con un mob cerca, en un QTE o derribado. Intensity 0..1; Pulse 0..1 sigue el latido.

uniform sampler2D InSampler;
uniform vec2 InSize;
uniform float Time;
uniform float Intensity;
uniform float Pulse;

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float k = clamp(Intensity, 0.0, 1.0);
    float t = Time * 6.2831853;
    vec2 uv = texCoord;
    vec2 c = uv - 0.5;
    float r = length(c);
    // distorsion: ondulacion lenta y un tiron hacia el centro con cada latido
    uv += vec2(sin(uv.y * 38.0 + t * 3.0), cos(uv.x * 30.0 + t * 2.0)) * 0.0016 * k;
    uv -= c * 0.012 * k * Pulse;
    // aberracion cromatica radial
    vec2 off = c * (0.004 + 0.016 * k) * (0.35 + r) * k;
    vec3 col;
    col.r = texture(InSampler, uv + off).r;
    col.g = texture(InSampler, uv).g;
    col.b = texture(InSampler, uv - off).b;
    // desaturacion hacia un tono enfermizo
    float g = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(g) * vec3(1.06, 0.97, 0.88), 0.5 * k);
    // grano que cambia cada frame
    float n = hash(floor(uv * InSize / 1.5) + fract(Time * 37.0) * 311.0) - 0.5;
    col += n * 0.09 * k;
    // vineta que late en rojo
    float v = smoothstep(0.30, 0.82, r);
    col *= 1.0 - v * (0.45 + 0.25 * Pulse) * k;
    col += vec3(0.30, 0.0, 0.0) * v * Pulse * k * 0.35;
    fragColor = vec4(col, 1.0);
}
