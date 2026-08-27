package be.winnetrie.mod.simpleserverutilities.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Keeps optional compat mixins out of Mixin processing when their mod is not on
 * the runtime classpath. @Pseudo remains on every Create mixin as a second
 * safety net, but this plugin avoids even attempting the Create mixin set on a
 * server/client that does not have Create installed.
 */
public final class SsuMixinConfigPlugin implements IMixinConfigPlugin {

    private boolean createPresent;

    @Override
    public void onLoad(String mixinPackage) {
        ClassLoader loader = SsuMixinConfigPlugin.class.getClassLoader();
        createPresent = loader.getResource("com/simibubi/create/Create.class") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (simpleName.startsWith("Create")) {
            return createPresent;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
