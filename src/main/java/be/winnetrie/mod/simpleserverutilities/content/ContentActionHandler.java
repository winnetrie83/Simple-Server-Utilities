package be.winnetrie.mod.simpleserverutilities.content;

@FunctionalInterface
public interface ContentActionHandler {
    PreparedContentAction prepare(
            ContentAction action,
            ContentActionContext context,
            ContentProgressionManager progression
    );
}
