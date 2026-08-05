package be.winnetrie.mod.simpleserverutilities.minigame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** Persisted player state used to guarantee restoration after a match or crash. */
public final class MinigamePlayerState {
    public static final int MAX_INVENTORY_SLOTS = 128;

    public List<JsonElement> inventory = new ArrayList<>();
    public List<JsonElement> activeEffects = new ArrayList<>();
    public String gameMode = "survival";
    public float health = 20.0F;
    public int foodLevel = 20;
    public float saturation = 5.0F;
    public int experienceLevel;
    public int totalExperience;
    public float experienceProgress;
    public float absorptionAmount;
    public int airSupply = 300;
    public int remainingFireTicks;
    public boolean mayFly;
    public boolean flying;

    public static MinigamePlayerState capture(ServerPlayer player) {
        MinigamePlayerState state = new MinigamePlayerState();
        var ops = RegistryOps.create(JsonOps.INSTANCE, player.level().registryAccess());
        int slots = Math.min(MAX_INVENTORY_SLOTS, player.getInventory().getContainerSize());
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty()) {
                state.inventory.add(JsonNull.INSTANCE);
            } else {
                JsonElement encoded = ItemStack.CODEC.encodeStart(ops, stack.copy()).result()
                        .orElseThrow(() -> new IllegalStateException("A player inventory stack could not be serialized safely."));
                state.inventory.add(encoded);
            }
        }
        for (MobEffectInstance effect : player.getActiveEffects()) {
            JsonElement encoded = MobEffectInstance.CODEC.encodeStart(ops, effect).result()
                    .orElseThrow(() -> new IllegalStateException("A player status effect could not be serialized safely."));
            state.activeEffects.add(encoded);
            if (state.activeEffects.size() >= 64) break;
        }
        state.gameMode = player.gameMode.getGameModeForPlayer().getName();
        state.health = player.getHealth();
        state.foodLevel = player.getFoodData().getFoodLevel();
        state.saturation = player.getFoodData().getSaturationLevel();
        state.experienceLevel = player.experienceLevel;
        state.totalExperience = player.totalExperience;
        state.experienceProgress = player.experienceProgress;
        state.absorptionAmount = player.getAbsorptionAmount();
        state.airSupply = player.getAirSupply();
        state.remainingFireTicks = player.getRemainingFireTicks();
        state.mayFly = ability(player, "mayfly", "mayFly");
        state.flying = ability(player, "flying");
        return state;
    }

    public void normalize() {
        if (inventory == null) inventory = new ArrayList<>();
        if (inventory.size() > MAX_INVENTORY_SLOTS) inventory = new ArrayList<>(inventory.subList(0, MAX_INVENTORY_SLOTS));
        if (activeEffects == null) activeEffects = new ArrayList<>();
        if (activeEffects.size() > 64) activeEffects = new ArrayList<>(activeEffects.subList(0, 64));
        gameMode = gameMode == null || gameMode.isBlank() ? "survival" : gameMode.trim();
        health = Float.isFinite(health) ? Math.max(0.1F, health) : 20.0F;
        foodLevel = Math.max(0, Math.min(20, foodLevel));
        saturation = Float.isFinite(saturation) ? Math.max(0.0F, Math.min(20.0F, saturation)) : 5.0F;
        experienceLevel = Math.max(0, experienceLevel);
        totalExperience = Math.max(0, totalExperience);
        experienceProgress = Float.isFinite(experienceProgress) ? Math.max(0.0F, Math.min(1.0F, experienceProgress)) : 0.0F;
        absorptionAmount = Float.isFinite(absorptionAmount) ? Math.max(0.0F, absorptionAmount) : 0.0F;
        airSupply = Math.max(0, airSupply);
    }

    public void restore(ServerPlayer player) {
        normalize();
        var ops = RegistryOps.create(JsonOps.INSTANCE, player.level().registryAccess());
        int slots = Math.min(player.getInventory().getContainerSize(), inventory.size());
        ArrayList<ItemStack> decoded = new ArrayList<>(slots);
        // Decode the complete snapshot before touching the live inventory. Corrupt
        // recovery data must never turn into a partially restored or empty inventory.
        for (int slot = 0; slot < slots; slot++) {
            final int storedSlot = slot;
            JsonElement encoded = inventory.get(slot);
            ItemStack stack = encoded == null || encoded.isJsonNull()
                    ? ItemStack.EMPTY
                    : ItemStack.CODEC.parse(ops, encoded).result()
                            .orElseThrow(() -> new IllegalStateException("Stored minigame inventory data is invalid at slot " + storedSlot + "."))
                            .copy();
            decoded.add(stack);
        }
        ArrayList<MobEffectInstance> decodedEffects = new ArrayList<>(activeEffects.size());
        for (int index = 0; index < activeEffects.size(); index++) {
            final int storedEffect = index;
            JsonElement encoded = activeEffects.get(index);
            if (encoded == null || encoded.isJsonNull()) continue;
            decodedEffects.add(MobEffectInstance.CODEC.parse(ops, encoded).result()
                    .orElseThrow(() -> new IllegalStateException("Stored minigame status effect is invalid at index " + storedEffect + ".")));
        }
        player.getInventory().clearContent();
        for (int slot = 0; slot < decoded.size(); slot++) player.getInventory().setItem(slot, decoded.get(slot));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.setGameMode(GameType.byName(gameMode, GameType.SURVIVAL));
        player.removeAllEffects();
        for (MobEffectInstance effect : decodedEffects) player.addEffect(effect);
        // Restore effects before health because Health Boost and similar effects change
        // the legal maximum health of the player.
        player.setHealth(Math.min(player.getMaxHealth(), health));
        player.getFoodData().setFoodLevel(foodLevel);
        player.getFoodData().setSaturation(saturation);
        player.experienceLevel = experienceLevel;
        player.totalExperience = totalExperience;
        player.experienceProgress = experienceProgress;
        player.setAbsorptionAmount(Math.min(player.getMaxAbsorption(), absorptionAmount));
        player.setAirSupply(Math.min(player.getMaxAirSupply(), airSupply));
        player.setRemainingFireTicks(remainingFireTicks);
        setAbility(player, mayFly, "mayfly", "mayFly");
        setAbility(player, flying, "flying");
        updateAbilities(player);
    }

    private static boolean ability(ServerPlayer player, String... names) {
        Object abilities = player.getAbilities();
        for (String name : names) {
            try {
                Field field = abilities.getClass().getField(name);
                return field.getBoolean(abilities);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    private static void setAbility(ServerPlayer player, boolean value, String... names) {
        Object abilities = player.getAbilities();
        for (String name : names) {
            try {
                Field field = abilities.getClass().getField(name);
                field.setBoolean(abilities, value);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static void updateAbilities(ServerPlayer player) {
        try {
            Method method = player.getClass().getMethod("onUpdateAbilities");
            method.invoke(player);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
