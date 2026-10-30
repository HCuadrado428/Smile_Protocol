package com.smileprotocol.block;

/**
 * Muebles del laboratorio (fase 24). GENERADO por tools/furniture.py: nombre, si se registra y su caja
 * de colision (pixeles, mirando al sur). No editar a mano.
 */
public final class FurnitureDefs {

    public record Def(String name, boolean searchable, int[] box) {}

    public static final Def[] ALL = {
            new Def("desk", true, new int[]{0, 0, 0, 16, 16, 16}),
            new Def("filing_cabinet", true, new int[]{2, 0, 1, 14, 16, 16}),
            new Def("medical_cabinet", true, new int[]{1, 0, 2, 15, 16, 15}),
            new Def("tool_cart", true, new int[]{1, 0, 2, 16, 15, 15}),
            new Def("supply_box", true, new int[]{0, 0, 2, 16, 9, 14}),
            new Def("metal_shelf", false, new int[]{0, 0, 2, 16, 16, 14}),
            new Def("workstation", false, new int[]{0, 0, 0, 16, 16, 16}),
            new Def("office_chair", false, new int[]{2, 0, 2, 14, 16, 14}),
            new Def("iv_stand", false, new int[]{3, 0, 3, 13, 16, 13}),
            new Def("wheelchair", false, new int[]{1, 0, 2, 15, 16, 16}),
            new Def("specimen_shelf", false, new int[]{0, 0, 2, 16, 15, 14}),
            new Def("sink", false, new int[]{1, 0, 0, 15, 16, 11}),
            new Def("trolley", false, new int[]{0, 0, 2, 16, 14, 14}),
    };

    private FurnitureDefs() {}
}
