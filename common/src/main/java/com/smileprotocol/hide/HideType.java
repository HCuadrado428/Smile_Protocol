package com.smileprotocol.hide;

/**
 * Tipo de escondite (README 15.1, 15.2), leido de {@code data/<ns>/hiding_spots/<id>.json}.
 *
 * @param viewCone       angulo total de giro de la camara desde dentro (grados)
 * @param breathDrain    fraccion de aliento que se gasta por segundo aguantando la respiracion
 * @param partial        ocultacion parcial (cortina): sin aguante, se ve la silueta
 * @param low            se entra tumbado (bajo camilla): pose de 0,6 bloques
 * @param overlay        superposicion de pantalla: slits, crack, low, cloth
 */
public record HideType(String id, int capacity, int enterTime, int exitTime, float noiseOnEnter, float viewCone,
                       float breathDrain, float searchPriority, boolean canBeLocked, boolean partial, boolean low,
                       String overlay) {

    public static HideType fallback(String id) {
        return new HideType(id, 1, 16, 14, 4, 70, 0.12f, 1, false, false, false, "slits");
    }
}
