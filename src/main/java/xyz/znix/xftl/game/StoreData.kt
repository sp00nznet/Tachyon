package xyz.znix.xftl.game

/**
 * Carries persistent information about a store, like its available items and prices.
 */
class StoreData {
    var availableResources = ResourceSet()

    init {
        // TODO initialise with proper values
        availableResources[Resource.FUEL] = 5
        availableResources[Resource.MISSILES] = 5
        availableResources[Resource.DRONES] = 5
    }
}
