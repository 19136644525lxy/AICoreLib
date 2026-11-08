package com.aicorelib.fabric;

import com.aicorelib.AICoreLib;
import net.fabricmc.api.ModInitializer;

public final class AICoreLibFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AICoreLib.init();
    }
}
