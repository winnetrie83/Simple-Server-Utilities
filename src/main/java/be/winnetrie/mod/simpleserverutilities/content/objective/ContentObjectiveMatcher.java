package be.winnetrie.mod.simpleserverutilities.content.objective;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import be.winnetrie.mod.simpleserverutilities.content.ContentEvent;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Stateless matcher and increment calculator for generic content objectives. */
public final class ContentObjectiveMatcher {
    private ContentObjectiveMatcher() {
    }

    public static boolean matches(ContentObjectiveDefinition definition, ContentEvent event) {
        if (definition == null || event == null || !definition.eventType.equals(event.type())) return false;
        if (!metadataMatches(definition, event)) return false;
        String subject = normalize(event.subject());
        return switch (definition.targetMode) {
            case ANY -> true;
            case EXACT, LIST -> definition.targets.stream().anyMatch(value -> normalize(value).equals(subject));
            case TAG -> tagMatches(definition, event);
        };
    }

    public static long contribution(ContentObjectiveDefinition definition, ContentEvent event) {
        if (!matches(definition, event)) return 0L;
        return switch (definition.aggregator) {
            case COUNT, UNIQUE -> 1L;
            case SUM, MAX -> Math.max(0L, event.amount());
        };
    }

    public static String uniqueKey(ContentObjectiveDefinition definition, ContentEvent event) {
        if (definition == null || event == null) return "";
        String metadataKey = event.metadata().getOrDefault("unique_key", "");
        if (!metadataKey.isBlank()) return metadataKey;
        if (!event.subject().isBlank()) return event.subject();
        return event.eventId().toString();
    }

    private static boolean metadataMatches(ContentObjectiveDefinition definition, ContentEvent event) {
        for (var entry : definition.metadata.entrySet()) {
            String expected = entry.getValue() == null ? "" : entry.getValue().trim();
            String actual = event.metadata().getOrDefault(entry.getKey(), "");
            if (!"*".equals(expected) && !expected.equalsIgnoreCase(actual)) return false;
        }
        return true;
    }

    private static boolean tagMatches(ContentObjectiveDefinition definition, ContentEvent event) {
        // Custom/third-party publishers can always provide their own comma-separated tag list.
        String raw = event.metadata().getOrDefault("tags", "");
        if (!raw.isBlank()) {
            Set<String> tags = Arrays.stream(raw.split(","))
                    .map(ContentObjectiveMatcher::normalize)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toSet());
            for (String expected : definition.targets) {
                String normalized = normalizeTag(expected);
                if (tags.contains(normalized) || tags.contains(stripTagPrefix(normalized))) return true;
            }
        }

        // Vanilla/static registry subjects do not need every event publisher to materialize all tags.
        for (String expected : definition.targets) {
            if (registryTagMatches(event.type(), event.subject(), expected)) return true;
        }
        return false;
    }

    private static boolean registryTagMatches(String eventType, String rawSubject, String rawTag) {
        String subject = normalize(rawSubject);
        String tag = stripTagPrefix(normalizeTag(rawTag));
        if (subject.isBlank() || tag.isBlank()) return false;
        try {
            ResourceLocation subjectId = ResourceLocation.parse(subject);
            ResourceLocation tagId = ResourceLocation.parse(tag);

            if (isBlockEvent(eventType)) {
                Block block = BuiltInRegistries.BLOCK.getOptional(subjectId).orElse(null);
                if (block == null) return false;
                TagKey<Block> key = TagKey.create(Registries.BLOCK, tagId);
                return BuiltInRegistries.BLOCK.getTag(key)
                        .map(values -> values.contains(BuiltInRegistries.BLOCK.wrapAsHolder(block)))
                        .orElse(false);
            }
            if (isItemEvent(eventType)) {
                Item item = BuiltInRegistries.ITEM.getOptional(subjectId).orElse(null);
                if (item == null) return false;
                TagKey<Item> key = TagKey.create(Registries.ITEM, tagId);
                return BuiltInRegistries.ITEM.getTag(key)
                        .map(values -> values.contains(BuiltInRegistries.ITEM.wrapAsHolder(item)))
                        .orElse(false);
            }
            if (isEntityEvent(eventType)) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(subjectId).orElse(null);
                if (type == null) return false;
                TagKey<EntityType<?>> key = TagKey.create(Registries.ENTITY_TYPE, tagId);
                return BuiltInRegistries.ENTITY_TYPE.getTag(key)
                        .map(values -> values.contains(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type)))
                        .orElse(false);
            }
        } catch (RuntimeException ignored) {
            // Invalid/missing tags are a non-match, never a gameplay-event failure.
        }
        return false;
    }

    private static boolean isBlockEvent(String type) {
        return ContentEventTypes.BLOCK_BROKEN.equals(type) || ContentEventTypes.BLOCK_PLACED.equals(type);
    }

    private static boolean isItemEvent(String type) {
        return ContentEventTypes.ITEM_CRAFTED.equals(type)
                || ContentEventTypes.ITEM_USED.equals(type)
                || ContentEventTypes.ITEM_CONSUMED.equals(type)
                || ContentEventTypes.AUCTION_SALE.equals(type)
                || ContentEventTypes.AUCTION_REVENUE.equals(type)
                || ContentEventTypes.AUCTION_PURCHASE.equals(type);
    }

    private static boolean isEntityEvent(String type) {
        return ContentEventTypes.ENTITY_KILLED.equals(type)
                || ContentEventTypes.DAMAGE_DEALT.equals(type)
                || ContentEventTypes.DAMAGE_TAKEN.equals(type);
    }

    private static String normalizeTag(String value) {
        String normalized = normalize(value);
        return normalized.startsWith("#") ? normalized : "#" + normalized;
    }

    private static String stripTagPrefix(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
