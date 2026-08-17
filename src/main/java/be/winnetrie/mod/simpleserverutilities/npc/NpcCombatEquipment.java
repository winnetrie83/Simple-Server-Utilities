package be.winnetrie.mod.simpleserverutilities.npc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantments;

/** Equipment-driven NPC combat helpers. Equipped stacks retain normal gameplay components/enchantments. */
final class NpcCombatEquipment {
    private NpcCombatEquipment() {}

    static double meleeDamage(LivingEntity source) {
        if (source == null) return 1.0D;
        if (source.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            return Math.max(0.0D, source.getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
        ItemStack weapon = source.getMainHandItem();
        if (weapon.isEmpty()) return 1.0D;
        try {
            return Math.max(0.0D, weapon.getAttributeModifiers().compute(
                    Attributes.ATTACK_DAMAGE, 1.0D, EquipmentSlot.MAINHAND));
        } catch (RuntimeException ignored) {
            return 1.0D;
        }
    }

    static boolean hasRangedWeapon(LivingEntity source) {
        return !rangedWeapon(source).isEmpty();
    }

    static boolean mainHandIsRanged(LivingEntity source) {
        return source != null && isRangedWeapon(source.getMainHandItem());
    }

    static InteractionHand rangedHand(LivingEntity source) {
        if (source != null && isRangedWeapon(source.getMainHandItem())) return InteractionHand.MAIN_HAND;
        return InteractionHand.OFF_HAND;
    }

    /**
     * Approximation used by SSU's generic ranged executor. Mainhand is preferred, but an offhand
     * bow/crossbow/trident is also valid so a sword+bow loadout can use both melee and ranged channels.
     */
    static double rangedDamage(LivingEntity source) {
        if (source == null) return 0.0D;
        ItemStack weapon = rangedWeapon(source);
        if (weapon.isEmpty()) return 0.0D;
        if (weapon.getItem() instanceof TridentItem || idContains(weapon, "trident")) {
            try {
                return Math.max(1.0D, weapon.getAttributeModifiers().compute(
                        Attributes.ATTACK_DAMAGE, 1.0D, weapon == source.getOffhandItem() ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND));
            } catch (RuntimeException ignored) {
                return Math.max(1.0D, meleeDamage(source));
            }
        }
        if (weapon.getItem() instanceof CrossbowItem || idContains(weapon, "crossbow")) return 9.0D;
        if (weapon.getItem() instanceof ProjectileWeaponItem || idContains(weapon, "bow")) {
            double damage = 6.0D;
            try {
                var enchantments = source.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                int power = weapon.getEnchantmentLevel(enchantments.getOrThrow(Enchantments.POWER));
                if (power > 0) damage += 0.5D * power + 0.5D;
            } catch (RuntimeException ignored) {
                // A modded registry/context may not expose the vanilla Power holder; keep base damage.
            }
            return damage;
        }
        return 0.0D;
    }

    static boolean rangedFlame(LivingEntity source) {
        ItemStack weapon = rangedWeapon(source);
        if (weapon.isEmpty()) return false;
        try {
            var enchantments = source.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            return weapon.getEnchantmentLevel(enchantments.getOrThrow(Enchantments.FLAME)) > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static int rangedPunch(LivingEntity source) {
        ItemStack weapon = rangedWeapon(source);
        if (weapon.isEmpty()) return 0;
        try {
            var enchantments = source.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            return Math.max(0, weapon.getEnchantmentLevel(enchantments.getOrThrow(Enchantments.PUNCH)));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static ItemStack rangedWeapon(LivingEntity source) {
        if (source == null) return ItemStack.EMPTY;
        ItemStack main = source.getMainHandItem();
        if (isRangedWeapon(main)) return main;
        ItemStack off = source.getOffhandItem();
        return isRangedWeapon(off) ? off : ItemStack.EMPTY;
    }

    private static boolean isRangedWeapon(ItemStack weapon) {
        return weapon != null && !weapon.isEmpty() && (weapon.getItem() instanceof ProjectileWeaponItem
                || weapon.getItem() instanceof TridentItem || rangedLookingId(weapon));
    }

    private static boolean idContains(ItemStack stack, String needle) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(needle);
    }

    static void repairEquipped(LivingEntity entity) {
        if (entity == null) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() != 0) stack.setDamageValue(0);
        }
    }

    private static boolean rangedLookingId(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.contains("bow") || id.contains("crossbow") || id.contains("trident");
    }
}
