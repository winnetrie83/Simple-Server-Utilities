package be.winnetrie.mod.simpleserverutilities.mail;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class MailComposeMenu extends AbstractContainerMenu {
    public static final int ATTACHMENT_SLOTS = 9;
    private static final int PLAYER_INVENTORY_START = ATTACHMENT_SLOTS;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_END = PLAYER_INVENTORY_END + 9;

    private static final int ATTACHMENT_X = 16;
    private static final int ATTACHMENT_Y = 226;
    private static final int PLAYER_INVENTORY_X = 119;
    private static final int PLAYER_INVENTORY_Y = 260;
    private static final int HOTBAR_Y = 326;

    private final Container attachments;
    private final int maxAttachments;
    private final boolean canSendItems;
    private final boolean canSendMoney;
    private final String formattedBalance;
    private boolean committed;

    public MailComposeMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, new SimpleContainer(ATTACHMENT_SLOTS),
                extraData.readVarInt(), extraData.readBoolean(), extraData.readBoolean(), extraData.readUtf(128));
    }

    public MailComposeMenu(int containerId, Inventory playerInventory, Container attachments,
            int maxAttachments, boolean canSendItems, boolean canSendMoney, String formattedBalance) {
        super(ModMailMenus.MAIL_COMPOSE.get(), containerId);
        checkContainerSize(attachments, ATTACHMENT_SLOTS);
        this.attachments = attachments;
        this.maxAttachments = Math.max(0, Math.min(ATTACHMENT_SLOTS, maxAttachments));
        this.canSendItems = canSendItems;
        this.canSendMoney = canSendMoney;
        this.formattedBalance = formattedBalance == null ? "" : formattedBalance;

        attachments.startOpen(playerInventory.player);
        for (int i = 0; i < ATTACHMENT_SLOTS; i++) {
            addSlot(new AttachmentSlot(
                    attachments,
                    i,
                    ATTACHMENT_X + i * 18,
                    ATTACHMENT_Y,
                    i < this.maxAttachments && canSendItems
            ));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18,
                        PLAYER_INVENTORY_Y + row * 18
                ));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    playerInventory,
                    column,
                    PLAYER_INVENTORY_X + column * 18,
                    HOTBAR_Y
            ));
        }
    }

    public int maxAttachments() { return maxAttachments; }
    public boolean canSendItems() { return canSendItems; }
    public boolean canSendMoney() { return canSendMoney; }
    public String formattedBalance() { return formattedBalance; }

    public List<ItemStack> attachmentCopies() {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < ATTACHMENT_SLOTS; i++) {
            ItemStack stack = attachments.getItem(i);
            if (!stack.isEmpty()) result.add(stack.copy());
        }
        return List.copyOf(result);
    }

    public void commitAndClear() {
        committed = true;
        for (int i = 0; i < ATTACHMENT_SLOTS; i++) attachments.setItem(i, ItemStack.EMPTY);
        broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return true;
        return PermissionService.getBoolean((net.minecraft.server.level.ServerPlayer) player,
                PermissionKeys.MAIL_ACCESS, true)
                && PermissionService.getBoolean((net.minecraft.server.level.ServerPlayer) player,
                PermissionKeys.MAIL_SEND, true);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        attachments.stopOpen(player);
        if (!player.level().isClientSide() && !committed) {
            clearContainer(player, attachments);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack raw = slot.getItem();
        ItemStack copy = raw.copy();

        if (index < ATTACHMENT_SLOTS) {
            if (!moveItemStackTo(raw, PLAYER_INVENTORY_START, HOTBAR_END, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(raw, 0, maxAttachments, false)) {
            if (index < PLAYER_INVENTORY_END) {
                if (!moveItemStackTo(raw, PLAYER_INVENTORY_END, HOTBAR_END, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(raw, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (raw.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (raw.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, raw);
        return copy;
    }

    private static final class AttachmentSlot extends Slot {
        private final boolean active;

        private AttachmentSlot(Container container, int slot, int x, int y, boolean active) {
            super(container, slot, x, y);
            this.active = active;
        }

        @Override public boolean mayPlace(ItemStack stack) { return active && !stack.isEmpty(); }
        @Override public boolean isActive() { return active; }
    }
}
