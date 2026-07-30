package be.winnetrie.mod.simpleserverutilities.mail;

import com.google.gson.JsonElement;

public final class MailItemAttachment {
    private JsonElement stack;
    private boolean claimed;

    public MailItemAttachment() {
    }

    public MailItemAttachment(JsonElement stack) {
        this.stack = stack == null ? null : stack.deepCopy();
    }

    public JsonElement getStack() {
        return stack == null ? null : stack.deepCopy();
    }

    public void setStack(JsonElement stack) {
        this.stack = stack == null ? null : stack.deepCopy();
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }
}
