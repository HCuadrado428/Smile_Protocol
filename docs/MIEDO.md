# Miedo: ritmo de las plantas

Resultado del test del miedo (noviembre de 2026): tension lenta, indefension, sustos fuertes e
inquietud rara; muchos monstruos pero vistos poco; objetivos con precio; plantas pequenas.

## Director del miedo (`fear/FearDirector`)

Por jugador y por planta (no en el tutorial ni en la sala final):

| Fase | Dura | Que pasa |
|---|---|---|
| Calma | 35-65 s | Casi nada; a veces un mueble cambia de sitio sin que lo veas |
| Tension | 40-65 s | Un susto falso cada 11-20 s: pasos por el techo o dentro de la pared, una puerta que se cierra a tu espalda, un mueble que te mira, luces que fallan detras |
| Pico | 6 s | Una silueta cruza el fondo del pasillo; si no hay sitio, un ruido que solo oyen ellos atrae al monstruo mas cercano |
| Respiro | 25-45 s | Nada (se alarga mientras haya un monstruo cerca) |

Con un monstruo a menos de 18 bloques no se inventa nada. Cuando uno te descubre (te mira de
frente, te ve y viene hacia ti) suena un golpe seco solo para ti y la fase salta a pico.

## Avisos del cuerpo (`client/ClientFear`)

Latido que se acelera a menos de 14 bloques de un monstruo y respiracion a menos de 8, sin decir
donde esta. Los pasos de los monstruos se oyen de lejos (el Paciente y el Apagon no hacen ruido).

## Vidas por planta (`fear/FloorRetry`)

Dos vidas por planta (`lives_per_floor`): la primera muerte te despierta en el ascensor de la planta
sin lo que cogiste en ella; la segunda es la muerte de siempre. Vale en todas las plantas, tambien
en la sala final.
