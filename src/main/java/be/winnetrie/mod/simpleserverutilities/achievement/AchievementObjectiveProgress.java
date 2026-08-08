package be.winnetrie.mod.simpleserverutilities.achievement;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AchievementObjectiveProgress {
    public long value;
    public Set<String> uniqueValues = new LinkedHashSet<>();
    public void normalize() {
        value = Math.max(0L, value);
        if (uniqueValues == null) uniqueValues = new LinkedHashSet<>();
        if (uniqueValues.size() > 4096) uniqueValues = new LinkedHashSet<>(uniqueValues.stream().limit(4096).toList());
    }
}
