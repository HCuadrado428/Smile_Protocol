#version 150

// Imagen de Smile Protocol (README 21). Dos capas:
//  - Base (siempre dentro de la partida): grado de color frio y sucio, negros aplastados, grano de
//    pelicula, vineta suave, aberracion cromatica en los bordes, polvo y rayas de vez en cuando.
//  - Tension (Intensity 0..1, con un smiler cerca, en un QTE o derribado): vision de tunel con el
//    borde borroso, ondulacion, tiron con cada latido (Pulse), imagen doble, desaturacion hacia el
//    rojo, lineas de interferencia y vineta que late en rojo. Calm = 1 quita los parpadeos.

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform float Intensity;
uniform float Pulse;
uniform float Base;
uniform float Calm;

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}

vec3 sampleRGB(vec2 uv, vec2 off) {
    return vec3(texture(DiffuseSampler, uv + off).r, texture(DiffuseSampler, uv).g, texture(DiffuseSampler, uv - off).b);
}

void main() {
    float k = clamp(Intensity, 0.0, 1.0);
    float b = clamp(Base, 0.0, 1.0);
    float t = Time * 6.2831853;
    float frame = floor(Time * 24.0);
    vec2 uv = texCoord;
    vec2 c = uv - 0.5;
    float r = length(c * vec2(InSize.x / InSize.y, 1.0)) / length(vec2(InSize.x / InSize.y, 1.0) * 0.5);

    // --- tension: deformaciones de la imagen ---
    uv += vec2(sin(uv.y * 38.0 + t * 3.0), cos(uv.x * 30.0 + t * 2.0)) * 0.0016 * k;
    uv -= c * 0.014 * k * Pulse;
    // lineas de interferencia que desplazan una franja (no si se piden pocos destellos)
    float band = step(0.985 - 0.04 * k, noise(vec2(uv.y * 12.0, frame * 0.7))) * (1.0 - Calm);
    uv.x += band * (hash(vec2(frame, uv.y)) - 0.5) * 0.02 * k;

    // aberracion cromatica: algo en los bordes siempre, mucha con tension
    vec2 off = c * ((0.0012 * b) + (0.004 + 0.016 * k) * k) * (0.35 + r);
    vec3 col = sampleRGB(uv, off);

    // vision de tunel: borde borroso (6 muestras hacia el centro) con tension
    if (k > 0.05) {
        vec3 blur = col;
        for (int i = 1; i <= 6; i++) {
            blur += sampleRGB(uv - c * 0.012 * float(i) * k, off);
        }
        blur /= 7.0;
        col = mix(col, blur, smoothstep(0.35, 0.95, r) * k);
        // imagen doble que tiembla con el latido
        vec3 ghost = texture(DiffuseSampler, uv + vec2(0.006, -0.003) * (0.5 + Pulse) * k).rgb;
        col = mix(col, max(col, ghost), 0.25 * k);
    }

    // --- base: grado de color de laboratorio abandonado ---
    float g = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 graded = mix(vec3(g), col, 0.82);                                   // algo menos de color
    graded *= vec3(0.96, 1.0, 1.04);                                         // sombras frias
    graded = mix(graded, graded * vec3(1.06, 1.02, 0.9), smoothstep(0.45, 1.0, g));   // luces amarillentas
    graded = max(graded - 0.025, 0.0) * 1.04;                                // negros aplastados
    col = mix(col, graded, b);

    // --- tension: desaturacion enfermiza hacia el rojo ---
    g = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(g) * vec3(1.12, 0.92, 0.84), 0.55 * k);

    // grano de pelicula (mas grueso con tension)
    float grainSize = mix(1.4, 2.2, k);
    float n = hash(floor(uv * InSize / grainSize) + fract(Time * 37.0) * 311.0) - 0.5;
    col += n * (0.035 * b + 0.08 * k) * (1.0 - g * 0.5);

    // polvo y rayas de pelicula, de vez en cuando
    if (Calm < 0.5) {
        float speck = step(0.9993, hash(floor(uv * InSize / 3.0) + frame * 13.1));
        col = mix(col, vec3(0.02), speck * 0.7 * b);
        float scratchX = hash(vec2(frame * 0.37, 7.0));
        float scratch = step(0.94, hash(vec2(frame * 0.11, 3.0))) * (1.0 - smoothstep(0.0, 0.0012, abs(uv.x - scratchX)));
        col += scratch * 0.08 * b;
    }

    // vineta: suave siempre; con tension, cerrada y latiendo en rojo
    float v = smoothstep(0.55, 1.15, r);
    col *= 1.0 - v * 0.45 * b;
    float vt = smoothstep(0.30, 0.95, r);
    col *= 1.0 - vt * (0.45 + 0.25 * Pulse) * k;
    col += vec3(0.32, 0.0, 0.0) * vt * Pulse * k * 0.35;

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
