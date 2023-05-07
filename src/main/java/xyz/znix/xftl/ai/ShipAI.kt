package xyz.znix.xftl.ai

import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.IRoomTargetingWeapon

class ShipAI(val ship: Ship, val player: Ship) {
    fun update(dt: Float) {
        // If the ship is dying, don't do anything.
        // TODO figure out where the boarder AI will live
        if (ship.isDead)
            return

        // Power up all the systems
        for (sys in ship.mainSystems) {
            for (i in 1..sys.energyLevels) sys.increasePower()
        }

        updateWeapons()

        // TODO only run when something significant happens
        updateTasks()

        // Check if we need to escape or surrender
        if (ship.health <= ship.surrenderHealth) {
            // Don't surrender repeatedly
            ship.surrenderHealth = 0

            // The surrender event must exist, it's XML tag did
            // for surrenderHealth to be set.
            ship.sys.shipUI.showEventDialogue(ship.spec!!.surrender!!.resolve())

            // Use an else-if so we don't try and surrender and escape in
            // the same update cycle.
        } else if (ship.health <= ship.escapeHealth) {
            // Show the event and start the timer running
            ship.escapeHealth = 0
            ship.sys.shipUI.showEventDialogue(ship.spec!!.escape!!.resolve())
            ship.escapeTimer = ship.spec.escapeTimer
        }

        val escapeTimer = ship.escapeTimer
        if (escapeTimer != null) {
            // For the FTL to charge, the engines and piloting must
            // be working, and a pilot must be present.
            val engines = ship.engines!!
            val piloting = ship.piloting!!
            val hasPilot = ship.friendlyCrew.any { it.room == piloting.room }

            if (engines.powerSelected > 0 && piloting.undamagedEnergy > 0 && hasPilot) {
                ship.escapeTimer = escapeTimer - dt
            }

            // SlickGame will check the escape timer, and remove
            // the ship if it runs out.
        }
    }

    private fun updateWeapons() {
        val weapons = ship.weapons ?: return

        // Does nothing if already fully powered
        weapons.increasePower()

        for (hp in ship.hardpoints) {
            val weapon = hp.weapon ?: continue

            if (!weapon.isCharged)
                continue

            when (weapon) {
                is IRoomTargetingWeapon -> {
                    weapon.fire(weapons, pickTarget())
                }
            }
        }
    }

    private fun pickTarget(): Room {
        return player.rooms[(Math.random() * player.rooms.size).toInt()]
    }

    private val assignments = HashMap<AbstractCrew, AITask?>()
    private val repairTasks = HashMap<Room, RepairTask>()
    private val manningTasks = ArrayList<ManningTask>()
    private val combatTasks = HashMap<AbstractCrew, CombatTask>()

    init {
        val tasks = manningTasks

        // Create manning tasks for all systems, even those where crew won't
        // do anything - those just have very low priorities, and serve
        // to disperse thee crew throughout the ship.
        for (system in ship.systems) {
            tasks += ManningTask(system.room!!)
        }
    }

    private fun updateTasks() {
        for (room in repairTasks.keys.toList()) {
            val sys = room.system
            check(sys != null)
            if (!sys.damaged)
                repairTasks.remove(room)
        }

        val tasks = ArrayList<AITask>(manningTasks)

        for (sys in ship.systems) {
            if (!sys.damaged)
                continue

            val room = sys.room!!
            val task = repairTasks[room] ?: RepairTask(room).also { repairTasks[room] = it }
            tasks += task
        }

        val outdatedCombatTasks = combatTasks.keys.filter { !ship.intruders.contains(it) }
        for (task in outdatedCombatTasks)
            combatTasks.remove(task)
        for (hostile in ship.intruders) {
            tasks += combatTasks.computeIfAbsent(hostile) { CombatTask(hostile) }
        }

        val allTasks = tasks.toHashSet()

        // Clear the assignee of tasks when that assignee is dead or missing
        for (task in tasks) {
            val assignee = task.assignee ?: continue
            if (!assignments.containsKey(assignee))
                task.assignee = null
        }

        // Exclude tasks that are already in progress
        tasks.removeIf { it.assignee != null }

        // Tasks in increasing order of importance. Use this order since removing the last
        // element of an ArrayList is very fast.
        tasks.sortByDescending { it.priority }

        // Make sure the assignments 1:1 matches the available crew
        if (!ship.friendlyCrew.containsAll(assignments.keys)) {
            val missingCrew = assignments.keys.filter { !ship.friendlyCrew.contains(it) }
            for (crew in missingCrew) {
                assignments.remove(crew)
            }
        }

        if (ship.friendlyCrew.size != assignments.size) {
            for (crew in ship.friendlyCrew) {
                if (!assignments.containsKey(crew))
                    assignments[crew] = null
            }
        }

        // Make sure the tasks and assignments match
        for ((crew, task) in assignments) {
            if (task == null) continue
            if (!allTasks.contains(task)) assignments[crew] = null
            task.assignee = crew
        }

        // Now find which crew are most suitable
        val candidates = assignments.entries.asSequence().sortedByDescending { it.value?.priority ?: 1000 }

        // TODO use isDangerous to prioritise different tasks for different races

        // Go through the crew, matching whoever is currently doing the least important task to work
        // on the most important as-of-yet unmanned task.
        for ((crew, current) in candidates) {
            if (tasks.isEmpty()) break

            // Grab the most important task this crewmember can respond to
            val task = tasks.lastOrNull { crewSuitableFor(crew, it) } ?: continue

            // If we're busy with something more important, leave it as-is.
            // Although the tasks will continue to decrease in priority and
            // the crew's tasks will continue to increase, we can't just stop
            // the loop here, as we might not be able to do a very important
            // task (for example, a defence drone when the shields are broken).
            if (current != null && allTasks.contains(current) && current.priority <= task.priority)
                continue

            tasks.remove(task)
            check(task.assignee == null)

            // Swap over the assignment
            current?.let { it.assignee = null }
            assignments[crew] = task
            task.assignee = crew
        }

        // Update all the tasks
        for (task in assignments.values) {
            task?.update()
        }
    }

    @Suppress("RedundantIf")
    private fun crewSuitableFor(crew: AbstractCrew, task: AITask): Boolean {
        if (task is CombatTask && !crew.canFight)
            return false

        return true
    }

    companion object {
        fun systemPriority(system: AbstractSystem): Int {
            // Copied from FTL, see doc/crew-ai
            return when (system) {
                is Shields -> 0
                is Weapons -> 1
                // TODO Clonebay=1
                // TODO Artillery=2
                is Oxygen -> 3
                is Engines, is Drones, is Piloting -> 4
                is Cloaking, is MindControl, is Hacking -> 5
                is Sensors, is BackupBattery -> 6
                is Doors -> 7
                else -> 8
            }
        }

        @Suppress("RedundantIf")
        fun isDangerous(crew: AbstractCrew, room: Room): Boolean {
            // As per doc/crew-ai, the room is dangerous:
            // - If a room is an airlock and an airlock door is open, it's dangerous
            // - If health<25% and oxygen<10% it's dangerous, if we can suffocate (ie, except lanius)
            // - If fireCount>0 and health<20%, it's dangerous unless we're immune to fire
            // - If fireCount>=4 then it's dangerous unless we're immune to fire
            // - This room is the medbay, and it's being hacked

            val healthFraction = crew.health / crew.maxHealth
            if (healthFraction < 0.25f && room.oxygen < 0.10f && crew.suffocationMultiplier > 0f) {
                return true
            }

            // TODO airlock doors
            // TODO fire
            // TODO medbay hacking

            return false
        }
    }
}

abstract class AITask {
    abstract val priority: Int

    var assignee: AbstractCrew? = null

    abstract fun update()
}

class RepairTask(val room: Room) : AITask() {
    override val priority: Int
        get() {
            // Oxygen gets a very high priority when it's low
            if (room.system is Oxygen && room.ship.averageOxygen < 0.25f)
                return 0

            // Shields is special
            if (room.system is Shields)
                return 1

            return ShipAI.systemPriority(room.system!!) + 48
        }

    override fun update() {
        val crew = assignee ?: return
        if (crew.pathingTarget?.room == room || crew.room == room) return
        crew.setTargetRoom(room)
    }
}

class ManningTask(val room: Room) : AITask() {
    override val priority: Int = run {
        val isHard = false // TODO

        // See doc/crew-ai
        val systemPriority = when (room.system) {
            is Piloting -> 64
            is Shields -> if (isHard) 67 else 65
            is Engines -> if (isHard) 65 else 67
            is Weapons -> 66 // TODO and artillery
            is Oxygen -> 68
            is Sensors -> 69
            is Doors, is Cloaking -> 70
            is Teleporter -> 71
            is Medbay -> 72
            else -> 1000
        }

        systemPriority
    }

    override fun update() {
        val crew = assignee ?: return
        if (crew.pathingTarget?.room == room || crew.room == room) return
        crew.setTargetRoom(room)
    }

    override fun equals(other: Any?): Boolean {
        return other is ManningTask && other.room == room
    }

    override fun hashCode(): Int {
        return room.hashCode()
    }
}

class CombatTask(val enemy: AbstractCrew) : AITask() {
    override val priority: Int
        get() {
            val system = enemy.room.system
            val systemPriority = system?.let { ShipAI.systemPriority(it) } ?: 8
            return systemPriority + 16
        }

    override fun update() {
        val crew = assignee ?: return
        if (crew.pathingTarget?.room == enemy.room || crew.room == enemy.room) return
        crew.setTargetRoom(enemy.room)
    }

    override fun equals(other: Any?): Boolean {
        return other is CombatTask && other.enemy == enemy
    }

    override fun hashCode(): Int {
        return enemy.hashCode()
    }
}
