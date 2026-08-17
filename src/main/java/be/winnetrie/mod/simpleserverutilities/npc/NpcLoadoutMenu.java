package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-synchronised ghost inventory for combat equipment and the nine-slot NPC loot table. */
public final class NpcLoadoutMenu extends AbstractContainerMenu {
    public static final int MODE_EQUIPMENT = 0;
    public static final int MODE_LOOT = 1;
    public static final int EQUIPMENT_SLOTS = 6;
    public static final int LOOT_SLOTS = 9;
    public static final int PLAYER_INVENTORY_X = 99;
    public static final int PLAYER_INVENTORY_Y = 137;
    public static final int PLAYER_HOTBAR_Y = 195;

    private final SimpleContainer ghost;
    private final Inventory playerInventory;
    private final String instanceId;
    private final int mode;
    private final int activeSlots;
    private final int[] chances = new int[LOOT_SLOTS];
    private int rolls;

    public NpcLoadoutMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, new SimpleContainer(LOOT_SLOTS),
                extraData.readUtf(36), extraData.readVarInt(), extraData.readVarInt(), readChances(extraData));
    }

    public NpcLoadoutMenu(int containerId, Inventory playerInventory, SimpleContainer ghost,
            String instanceId, int mode, int rolls, int[] chances) {
        super(ModNpcMenus.NPC_LOADOUT.get(), containerId);
        checkContainerSize(ghost, LOOT_SLOTS);
        this.ghost = ghost;
        this.playerInventory = playerInventory;
        this.instanceId = instanceId == null ? "" : instanceId;
        this.mode = mode == MODE_LOOT ? MODE_LOOT : MODE_EQUIPMENT;
        this.activeSlots = this.mode == MODE_LOOT ? LOOT_SLOTS : EQUIPMENT_SLOTS;
        this.rolls = Math.max(1, Math.min(100, rolls));
        for (int i = 0; i < LOOT_SLOTS; i++) {
            this.chances[i] = chances != null && i < chances.length
                    ? Math.max(1, Math.min(10_000, chances[i])) : 10_000;
        }
        ghost.startOpen(playerInventory.player);
        for (int i = 0; i < activeSlots; i++) {
            int x = mode == MODE_LOOT ? 16 + i * 36 : 42 + i * 46;
            addSlot(new GhostSlot(ghost, i, x, 54));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column,
                    PLAYER_INVENTORY_X + column * 18, PLAYER_HOTBAR_Y));
        }
    }

    public String instanceId() { return instanceId; }
    public int mode() { return mode; }
    public int activeSlots() { return activeSlots; }
    public int rolls() { return rolls; }
    public int chance(int slot) { return slot >= 0 && slot < LOOT_SLOTS ? chances[slot] : 10_000; }
    public void updateLootSettings(int rolls, int[] values) {
        this.rolls = Math.max(1, Math.min(100, rolls));
        for (int i = 0; i < LOOT_SLOTS; i++) {
            chances[i] = values != null && i < values.length
                    ? Math.max(1, Math.min(10_000, values[i])) : 10_000;
        }
    }

    public List<ItemStack> ghostCopies() {
        List<ItemStack> result = new ArrayList<>(activeSlots);
        for (int i = 0; i < activeSlots; i++) result.add(ghost.getItem(i).copy());
        return List.copyOf(result);
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ClickType input, Player player) {
        if (slotIndex >= 0 && slotIndex < activeSlots) {
            if (input == ClickType.PICKUP) {
                ItemStack carried = getCarried();
                if (carried.isEmpty()) ghost.setItem(slotIndex, ItemStack.EMPTY);
                else ghost.setItem(slotIndex, ghostCopy(carried));
                broadcastChanges();
                return;
            }
            if (input == ClickType.QUICK_MOVE || input == ClickType.THROW) {
                ghost.setItem(slotIndex, ItemStack.EMPTY);
                broadcastChanges();
                return;
            }
            if (input == ClickType.SWAP && buttonNum >= 0 && buttonNum < 9) {
                ghost.setItem(slotIndex, ghostCopy(playerInventory.getItem(buttonNum)));
                broadcastChanges();
                return;
            }
        }
        if (input == ClickType.QUICK_MOVE && slotIndex >= activeSlots && slotIndex < slots.size()) {
            Slot source = slots.get(slotIndex);
            if (source != null && source.hasItem()) {
                int target = firstEmptyGhost();
                if (target >= 0) {
                    ghost.setItem(target, ghostCopy(source.getItem()));
                    broadcastChanges();
                }
            }
            return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    private ItemStack ghostCopy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        return mode == MODE_EQUIPMENT ? stack.copyWithCount(1) : stack.copy();
    }

    private int firstEmptyGhost() {
        for (int i = 0; i < activeSlots; i++) if (ghost.getItem(i).isEmpty()) return i;
        return -1;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot != null && slots.indexOf(slot) >= activeSlots;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return true;
        return player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && NpcEditorService.canAdmin(serverPlayer)
                && SimpleServerUtilities.NPCS.instance(instanceId) != null;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        ghost.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < activeSlots || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (source == null || !source.hasItem()) return ItemStack.EMPTY;
        int target = firstEmptyGhost();
        if (target < 0) return ItemStack.EMPTY;
        ItemStack copy = ghostCopy(source.getItem());
        ghost.setItem(target, copy);
        broadcastChanges();
        return copy;
    }

    private static int[] readChances(RegistryFriendlyByteBuf buffer) {
        int[] result = new int[LOOT_SLOTS];
        for (int i = 0; i < result.length; i++) result[i] = buffer.readVarInt();
        return result;
    }

    private static final class GhostSlot extends Slot {
        private GhostSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
    }
}
