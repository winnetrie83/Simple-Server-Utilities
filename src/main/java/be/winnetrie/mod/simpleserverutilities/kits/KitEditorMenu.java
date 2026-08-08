package be.winnetrie.mod.simpleserverutilities.kits;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Nine-slot ghost kit editor with real inventory-like left/right click semantics. */
public final class KitEditorMenu extends AbstractContainerMenu {
    public static final int KIT_SLOTS = 9;
    public static final int INV_X = 17, INV_Y = 91, HOTBAR_Y = 149;
    private final SimpleContainer ghost;
    private final Inventory playerInventory;
    private final String kitId;

    public KitEditorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(KIT_SLOTS), buf.readUtf(64));
    }

    public KitEditorMenu(int id, Inventory inv, SimpleContainer ghost, String kitId) {
        super(ModKitMenus.KIT_EDITOR.get(), id);
        this.ghost = ghost;
        this.playerInventory = inv;
        this.kitId = kitId;
        ghost.startOpen(inv.player);
        for (int i = 0; i < 9; i++) addSlot(new GhostSlot(ghost, i, 17 + i * 18, 42));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
    }

    public String kitId() { return kitId; }
    public List<ItemStack> copies() {
        ArrayList<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < 9; i++) out.add(ghost.getItem(i).copy());
        return List.copyOf(out);
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (slot >= 0 && slot < KIT_SLOTS) {
            if (input == ContainerInput.PICKUP) {
                ItemStack carried = getCarried();
                ItemStack current = ghost.getItem(slot);
                if (carried.isEmpty()) {
                    if (button == 1 && !current.isEmpty()) {
                        ItemStack updated = current.copy();
                        updated.shrink(1);
                        ghost.setItem(slot, updated.isEmpty() ? ItemStack.EMPTY : updated);
                    } else {
                        ghost.setItem(slot, ItemStack.EMPTY);
                    }
                } else if (button == 1) {
                    if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, carried)) {
                        ItemStack updated = current.copy();
                        updated.setCount(Math.min(updated.getMaxStackSize(), updated.getCount() + 1));
                        ghost.setItem(slot, updated);
                    } else {
                        ItemStack one = carried.copy();
                        one.setCount(1);
                        ghost.setItem(slot, one);
                    }
                } else {
                    ghost.setItem(slot, carried.copy());
                }
                broadcastChanges();
                return;
            }
            if (input == ContainerInput.QUICK_MOVE || input == ContainerInput.THROW) {
                ghost.setItem(slot, ItemStack.EMPTY);
                broadcastChanges();
                return;
            }
            if (input == ContainerInput.SWAP && button >= 0 && button < 9) {
                ghost.setItem(slot, playerInventory.getItem(button).copy());
                broadcastChanges();
                return;
            }
        }
        if (input == ContainerInput.QUICK_MOVE && slot >= KIT_SLOTS && slot < slots.size()) {
            Slot source = slots.get(slot);
            if (source.hasItem()) for (int i = 0; i < KIT_SLOTS; i++) if (ghost.getItem(i).isEmpty()) {
                ghost.setItem(i, source.getItem().copy());
                broadcastChanges();
                break;
            }
            return;
        }
        super.clicked(slot, button, input, player);
    }

    @Override public boolean canDragTo(Slot slot) { return slot != null && slots.indexOf(slot) >= KIT_SLOTS; }
    @Override public boolean stillValid(Player player) { return true; }
    @Override public void removed(Player player) { super.removed(player); ghost.stopOpen(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    private static final class GhostSlot extends Slot {
        GhostSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
    }
}
