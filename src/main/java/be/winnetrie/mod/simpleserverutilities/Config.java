package be.winnetrie.mod.simpleserverutilities;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_PLAYER_CLAIMS = BUILDER
            .comment("Enable player chunk claims.")
            .define("enablePlayerClaims", true);

    public static final ModConfigSpec.IntValue MAX_PLAYER_CLAIMS = BUILDER
            .comment("Maximum number of chunks a player can claim.")
            .defineInRange("maxPlayerClaims", 25, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue ENABLE_ADMIN_REGIONS = BUILDER
            .comment("Enable admin/server regions.")
            .define("enableAdminRegions", true);

    public static final ModConfigSpec.BooleanValue ENABLE_PERMISSION_SYSTEM = BUILDER
            .comment("Enable the internal permission system. Currently prepared for future use.")
            .define("enablePermissionSystem", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}