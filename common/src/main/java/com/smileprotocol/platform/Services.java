package com.smileprotocol.platform;

import java.util.ServiceLoader;

/** Acceso a la implementacion de {@link Platform} del loader en uso. */
public final class Services {

    public static final Platform PLATFORM = load(Platform.class);

    private Services() {}

    private static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, Services.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Falta la implementacion de " + type.getName()));
    }
}
