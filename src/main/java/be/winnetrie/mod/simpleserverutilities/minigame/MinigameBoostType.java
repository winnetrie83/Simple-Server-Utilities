package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.Locale;

/** Built-in temporary pickup types shared by Capture the Flag and Domination. */
public enum MinigameBoostType {
    SPEED("speed", "Speed", "minecraft:golden_boots",
            "minecraft:entity.firework_rocket.launch", 0.85F, 1.20F),
    REGENERATION("regeneration", "Regeneration", "minecraft:golden_apple",
            "minecraft:block.beacon.power_select", 0.90F, 1.15F),
    ARMOR("armor", "Armor", "minecraft:diamond_chestplate",
            "minecraft:item.armor.equip_diamond", 1.00F, 1.00F),
    JUMP("jump", "Jump", "minecraft:rabbit_foot",
            "minecraft:entity.wind_charge.throw", 0.90F, 1.10F);

    private final String id;
    private final String label;
    private final String itemId;
    private final String soundId;
    private final float soundVolume;
    private final float soundPitch;

    MinigameBoostType(String id, String label, String itemId, String soundId,
                      float soundVolume, float soundPitch) {
        this.id = id;
        this.label = label;
        this.itemId = itemId;
        this.soundId = soundId;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
    }

    public String id() { return id; }
    public String label() { return label; }
    public String itemId() { return itemId; }
    public String soundId() { return soundId; }
    public float soundVolume() { return soundVolume; }
    public float soundPitch() { return soundPitch; }

    public static MinigameBoostType parse(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (MinigameBoostType type : values()) if (type.id.equals(value)) return type;
        return SPEED;
    }
}
