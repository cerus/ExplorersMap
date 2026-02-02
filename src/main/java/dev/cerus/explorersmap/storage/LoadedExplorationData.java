package dev.cerus.explorersmap.storage;

public record LoadedExplorationData(
        ExplorationDataFile file,
        ExplorationData data
) {
}
