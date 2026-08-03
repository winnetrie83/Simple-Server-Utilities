package be.winnetrie.mod.simpleserverutilities.content;

@FunctionalInterface
public interface ContentEventListener {
    void onContentEvent(ContentEvent event);
}
