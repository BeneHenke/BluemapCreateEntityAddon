package eu.cronmoth.createentityaddon.rendering.sublevel;

import de.bluecolored.bluemap.core.map.BmMap;
import de.bluecolored.bluemap.core.map.hires.HiresModelManager;
import de.bluecolored.bluemap.core.map.hires.HiresModelRenderer;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import eu.cronmoth.createentityaddon.AddonLog;

import java.lang.reflect.Field;

public class SubLevelInstaller {

    private SubLevelInstaller() {}

    public static void install(BmMap map) throws ReflectiveOperationException {
        Field managerRenderer = HiresModelManager.class.getDeclaredField("renderer");
        managerRenderer.setAccessible(true);
        HiresModelRenderer current = (HiresModelRenderer) managerRenderer.get(map.getHiresModelManager());
        if (current instanceof SubLevelHiresModelRenderer) return;

        Field settingsField = HiresModelRenderer.class.getDeclaredField("renderSettings");
        settingsField.setAccessible(true);
        RenderSettings renderSettings = (RenderSettings) settingsField.get(current);

        managerRenderer.set(map.getHiresModelManager(),
                new SubLevelHiresModelRenderer(map.getResourcePack(), map.getTextureGallery(), renderSettings));
        AddonLog.info("sub-level rendering enabled for map '" + map.getId() + "'");
    }
}
