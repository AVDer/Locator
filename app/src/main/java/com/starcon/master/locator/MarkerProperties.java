package com.starcon.master.locator;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;

public final class MarkerProperties {
    private static float[] colours = {
            BitmapDescriptorFactory.HUE_BLUE,
            BitmapDescriptorFactory.HUE_GREEN,
            BitmapDescriptorFactory.HUE_VIOLET,
            BitmapDescriptorFactory.HUE_ORANGE,
            BitmapDescriptorFactory.HUE_CYAN,
            BitmapDescriptorFactory.HUE_YELLOW,
            BitmapDescriptorFactory.HUE_AZURE,
            BitmapDescriptorFactory.HUE_MAGENTA,
            BitmapDescriptorFactory.HUE_ROSE};
    public static float MarkerColourByIndex(int index) {
        return colours[index];
    }
}
