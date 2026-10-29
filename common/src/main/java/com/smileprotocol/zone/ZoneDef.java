package com.smileprotocol.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Definicion de una zona ({@code data/<ns>/zone/<id>.json}, README 6.5):
 * <pre>
 * { "template": "smileprotocol:zones/start_corridor",
 *   "kind": "start" | "intermediate" | "final",
 *   "difficulty": 1,
 *   "entry": [x, y, z], "entry_yaw": 180 }
 * </pre>
 * El resto de puntos (aparicion de mobs, fusibles, cabina de salida...) van dentro de la
 * plantilla como bloques de estructura en modo DATA con metadatos {@code sp:<marcador>}, para
 * que el mapa se pueda disenar dentro del juego (README 22). {@code entry} es opcional si la
 * plantilla tiene el marcador {@code sp:entry}.
 */
public record ZoneDef(ResourceLocation id, ResourceLocation template, ZoneKind kind, int difficulty,
                      @Nullable BlockPos entry, float entryYaw, String name) {
}
