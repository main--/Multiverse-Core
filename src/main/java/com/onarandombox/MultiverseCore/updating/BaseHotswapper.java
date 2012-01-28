package com.onarandombox.MultiverseCore.updating;

import org.bukkit.plugin.PluginManager;

import com.onarandombox.MultiverseCore.MultiverseCore;

public class BaseHotswapper implements Hotswapper {
    @Override
    public final void hotswap(MultiverseCore oldCore) {
        // 1. unload old plugin
        PluginManager pm = oldCore.getServer().getPluginManager();
        pm.disablePlugin(oldCore);
        
        // TODO actual unloading
        
        // 2. specific work
        doSpecificHotswapWork();
        
        // 3.
    }
    
    /*
     * Other hotswappers can override this.
     */
    public void doSpecificHotswapWork() {
    }
}
