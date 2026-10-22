package net.staro.nomnom.module.modules;

import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.event.listener.SafeListener;
import net.staro.nomnom.events.camera.CameraUpdateEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

public class Heaven extends NomnomModule {
    public Heaven(Nomnom nomnom) {
        super(nomnom, "Heaven", Category.RENDER, "Shows you what heaven is like.");
    }

    @SafeListener
    @SuppressWarnings("unused")
    public void onCameraUpdate(CameraUpdateEvent event) {
        mc.worldRenderer.reload();
    }

}
