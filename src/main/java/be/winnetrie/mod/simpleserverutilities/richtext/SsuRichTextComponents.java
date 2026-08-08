package be.winnetrie.mod.simpleserverutilities.richtext;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Converts SSU's bounded legacy-format storage into normal vanilla Components. */
public final class SsuRichTextComponents {
    private SsuRichTextComponents() {}

    public static MutableComponent parse(String raw) {
        MutableComponent root = Component.empty();
        if (raw == null || raw.isEmpty()) return root;
        List<ChatFormatting> active = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (int i=0;i<raw.length();i++) {
            char c=raw.charAt(i);
            if(c=='\u00A7'&&i+1<raw.length()){
                ChatFormatting formatting=ChatFormatting.getByCode(raw.charAt(i+1));
                if(formatting!=null){flush(root,text,active);i++;
                    if(formatting==ChatFormatting.RESET)active.clear();
                    else if (isColor(formatting)) {
                        active.removeIf(SsuRichTextComponents::isColor);
                        active.removeIf(v -> v == ChatFormatting.BOLD
                                || v == ChatFormatting.ITALIC
                                || v == ChatFormatting.UNDERLINE
                                || v == ChatFormatting.STRIKETHROUGH
                                || v == ChatFormatting.OBFUSCATED);
                        active.add(formatting);
                    }
                    else if(!active.contains(formatting))active.add(formatting);
                    continue;
                }
            }
            text.append(c);
        }
        flush(root,text,active);return root;
    }

    private static boolean isColor(ChatFormatting formatting) {
        return switch (formatting) {
            case BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA,
                    DARK_RED, DARK_PURPLE, GOLD, GRAY,
                    DARK_GRAY, BLUE, GREEN, AQUA,
                    RED, LIGHT_PURPLE, YELLOW, WHITE -> true;
            default -> false;
        };
    }

    private static void flush(MutableComponent root,StringBuilder text,List<ChatFormatting> active){
        if(text.isEmpty())return;MutableComponent part=Component.literal(text.toString());
        if(!active.isEmpty())part=part.withStyle(active.toArray(ChatFormatting[]::new));root.append(part);text.setLength(0);
    }
}
