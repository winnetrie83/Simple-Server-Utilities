package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.NpcLoadoutOpenRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcLoadoutResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcLoadoutSavePayload;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Opens and commits the real inventory-backed NPC equipment and loot editors. */
public final class NpcLoadoutService {
    private NpcLoadoutService() {
    }

    public static void handleOpen(NpcLoadoutOpenRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npcs")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        open(player, payload.instanceId(), payload.mode());
    }

    public static boolean open(ServerPlayer player, String rawInstanceId, int requestedMode) {
        if (!NpcEditorService.canAdmin(player)) return false;
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(rawInstanceId);
        NpcDefinition definition = SimpleServerUtilities.NPCS.definitionFor(instance);
        if (instance == null || definition == null) return false;
        int mode = requestedMode == NpcLoadoutMenu.MODE_LOOT ? NpcLoadoutMenu.MODE_LOOT : NpcLoadoutMenu.MODE_EQUIPMENT;
        HolderLookup.Provider registries = player.level().registryAccess();
        SimpleContainer ghost = new SimpleContainer(NpcLoadoutMenu.LOOT_SLOTS);
        if (mode == NpcLoadoutMenu.MODE_EQUIPMENT) {
            ghost.setItem(0, equipment(NpcItemCodec.decode(registries, definition.mainHandStack, definition.mainHandItem, 1)));
            ghost.setItem(1, equipment(NpcItemCodec.decode(registries, definition.offHandStack, definition.offHandItem, 1)));
            ghost.setItem(2, equipment(NpcItemCodec.decode(registries, definition.headStack, definition.headItem, 1)));
            ghost.setItem(3, equipment(NpcItemCodec.decode(registries, definition.chestStack, definition.chestItem, 1)));
            ghost.setItem(4, equipment(NpcItemCodec.decode(registries, definition.legsStack, definition.legsItem, 1)));
            ghost.setItem(5, equipment(NpcItemCodec.decode(registries, definition.feetStack, definition.feetItem, 1)));
        } else {
            for (int i = 0; i < Math.min(9, definition.loot.size()); i++) {
                NpcLootEntry entry = definition.loot.get(i);
                if (entry != null) ghost.setItem(i, NpcItemCodec.decode(registries, entry.stack, entry.itemId, entry.count));
            }
        }
        int[] chances = new int[9];
        for (int i = 0; i < 9; i++) {
            chances[i] = i < definition.loot.size() && definition.loot.get(i) != null
                    ? definition.loot.get(i).chanceHundredthPercent : 10_000;
        }
        int rolls = definition.lootRolls;
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new NpcLoadoutMenu(containerId, inventory, ghost,
                        instance.id, mode, rolls, chances),
                Component.literal(mode == NpcLoadoutMenu.MODE_LOOT ? "NPC Loot Table" : "NPC Combat Equipment")
        ), buffer -> {
            buffer.writeUtf(instance.id, 36); buffer.writeVarInt(mode); buffer.writeVarInt(rolls);
            for (int chance : chances) buffer.writeVarInt(chance);
        });
        return true;
    }

    public static void handleSave(NpcLoadoutSavePayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npcs")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        Result result = save(player, payload);
        PacketDistributor.sendToPlayer(player,
                new NpcLoadoutResultPayload(result.success(), result.message(), payload.requestId()));
        if (result.success()) player.closeContainer();
    }

    private static Result save(ServerPlayer player, NpcLoadoutSavePayload payload) {
        if (!NpcEditorService.canAdmin(player)) return Result.fail("NPC administration is not allowed.");
        if (!(player.containerMenu instanceof NpcLoadoutMenu menu) || menu.containerId != payload.containerId()) {
            return Result.fail("The NPC inventory editor is no longer current.");
        }
        if (menu.mode() != payload.mode()) return Result.fail("The NPC inventory editor mode changed.");
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(menu.instanceId());
        NpcDefinition existing = SimpleServerUtilities.NPCS.definitionFor(instance);
        if (instance == null || existing == null) return Result.fail("The NPC no longer exists.");
        NpcDefinition updated = existing.copy();
        HolderLookup.Provider registries = player.level().registryAccess();
        List<ItemStack> stacks = menu.ghostCopies();
        if (menu.mode() == NpcLoadoutMenu.MODE_EQUIPMENT) {
            updated.mainHandStack = NpcItemCodec.encode(registries, item(stacks, 0, true));
            updated.offHandStack = NpcItemCodec.encode(registries, item(stacks, 1, true));
            updated.headStack = NpcItemCodec.encode(registries, item(stacks, 2, true));
            updated.chestStack = NpcItemCodec.encode(registries, item(stacks, 3, true));
            updated.legsStack = NpcItemCodec.encode(registries, item(stacks, 4, true));
            updated.feetStack = NpcItemCodec.encode(registries, item(stacks, 5, true));
            updated.mainHandItem = updated.offHandItem = updated.headItem = "";
            updated.chestItem = updated.legsItem = updated.feetItem = "";
        } else {
            menu.updateLootSettings(payload.rolls(), payload.chances());
            updated.lootRolls = menu.rolls();
            updated.customLootEnabled = true;
            updated.loot.clear();
            for (int i = 0; i < 9; i++) {
                NpcLootEntry entry = new NpcLootEntry();
                ItemStack stack = item(stacks, i, false);
                entry.stack = NpcItemCodec.encode(registries, stack);
                entry.itemId = "";
                entry.count = stack.isEmpty() ? 1 : stack.getCount();
                entry.chanceHundredthPercent = menu.chance(i);
                updated.loot.add(entry);
            }
        }
        updated.normalize();
        if (!SimpleServerUtilities.NPCS.saveDefinition(existing.id, updated)) {
            return Result.fail("The NPC loadout could not be saved.");
        }
        return Result.ok(menu.mode() == NpcLoadoutMenu.MODE_LOOT
                ? "NPC loot table saved." : "NPC combat equipment saved.");
    }

    private static ItemStack item(List<ItemStack> values, int index, boolean equipment) {
        if (values == null || index < 0 || index >= values.size()) return ItemStack.EMPTY;
        ItemStack stack = values.get(index);
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        return equipment ? stack.copyWithCount(1) : stack.copy();
    }

    private static ItemStack equipment(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    private record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message); }
    }
}
