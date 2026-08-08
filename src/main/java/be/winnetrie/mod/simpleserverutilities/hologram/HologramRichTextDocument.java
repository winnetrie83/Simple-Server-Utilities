package be.winnetrie.mod.simpleserverutilities.hologram;

import java.util.function.UnaryOperator;

import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichTextDocument;

/**
 * @deprecated Compatibility facade. New SSU features should use {@link SsuRichTextDocument}.
 */
@Deprecated
public final class HologramRichTextDocument extends SsuRichTextDocument {
    public HologramRichTextDocument(String encodedText) {
        super(encodedText, HologramRichText::normalize, HologramRichText.MAX_STORED_CHARACTERS);
    }

    public HologramRichTextDocument(String encodedText, UnaryOperator<String> normalizer, int maximumStoredCharacters) {
        super(encodedText, normalizer, maximumStoredCharacters);
    }
}
