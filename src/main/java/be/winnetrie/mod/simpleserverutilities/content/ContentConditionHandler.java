package be.winnetrie.mod.simpleserverutilities.content;

@FunctionalInterface
public interface ContentConditionHandler {
    ContentConditionResult evaluate(
            ContentCondition condition,
            ContentConditionContext context,
            ContentConditionEngine engine,
            ContentProgressionManager progression
    );
}
