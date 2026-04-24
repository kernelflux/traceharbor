package com.kernelflux.traceharborsample;

public final class LeakStore {
    private static Object leakedObject;

    private LeakStore() {
    }

    public static void hold(Object object) {
        leakedObject = object;
    }

    public static void clear() {
        leakedObject = null;
    }

    public static boolean hasLeak() {
        return leakedObject != null;
    }
}
