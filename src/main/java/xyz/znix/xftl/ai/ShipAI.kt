package xyz.znix.xftl.ai

import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.IRoomTargetingWeapon
import xyz.znix.xftl.weapons.LaserBlueprint
import xyz.znix.xftl.weapons.MissileBlueprint

class ShipAI(val ship: Ship, val player: Ship) {
    fun update(dt: Float) {
        // Power up all the systems
        for (room in ship.rooms) {
            val sys = room.system as? MainSystem ?: continue
            for (i in 1..sys.energyLevels) sys.increasePower()
        }

        updateWeapons()

        // TODO only run when something significant happens
        updateTasks()
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

    init {
        val tasks = manningTasks
        ship.piloting?.let { tasks += ManningTask(it.room!!) }
        ship.engines?.let { tasks += ManningTask(it.room!!) }
        ship.shields?.let { tasks += ManningTask(it.room!!) }
        ship.weapons?.let { tasks += ManningTask(it.room!!) }
    }

    private fun updateTasks() {
        for (room in repairTasks.keys.toList()) {
            val sys = room.system
            check(sys != null)
            if (!sys.damaged)
                repairTasks.remove(room)
        }

        val tasks = ArrayList<AITask>(manningTasks)

        for (room in ship.rooms) {
            val sys = room.system ?: continue
            if (!sys.damaged) continue
            val task = repairTasks[room] ?: RepairTask(room).also { repairTasks[room] = it }
            tasks += task
        }

        val allTasks = tasks.toHashSet()

        // Exclude tasks that are already in progress
        tasks.removeIf { it.assignee != null }

        // Tasks in increasing order of importance. Use this order since removing the last
        // element of an ArrayList is very fast.
        tasks.sortBy { it.priority }

        // Make sure the assignments 1:1 matches the available crew
        if (!ship.crew.containsAll(assignments.keys)) {
            assignments.filterKeys { ship.crew.contains(it) }
        }

        if (ship.crew.size != assignments.size) {
            for (crew in ship.crew) {
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
        val candidates = assignments.entries.asSequence().sortedBy { it.value?.priority ?: -1 }

        // Go through the crew, matching whoever is currently doing the least important task to work
        // on the most important as-of-yet unmanned task.
        // TODO support multi-crew repairs - if shields are down, don't have someone fixing doors
        for ((crew, current) in candidates) {
            if (tasks.isEmpty()) break

            // Pop the most important task
            val task = tasks.removeAt(tasks.size - 1)

            // If we're busy with something more important, stop here since the tasks will continue
            // to decrease in priority and the crew will continue to increase
            if (current != null && allTasks.contains(current) && current.priority >= task.priority) break

            check(task.assignee == null)

            // Swap over the assignment
            current?.let { it.assignee = null }
            assignments[crew] = task
            task.assignee = crew

            task.update()
        }
    }
}

abstract class AITask {
    abstract val priority: Int

    var assignee: AbstractCrew? = null

    abstract fun update()
}

class RepairTask(val room: Room) : AITask() {
    override val priority: Int = run {
        val systemPriority = when (room.system) {
            is Shields -> 100
            is Piloting -> 90
            is Engines -> 80
            is Weapons -> 70
            is Oxygen -> 60
            else -> 50
        }

        systemPriority
    }

    override fun update() {
        val crew = assignee ?: return
        if (crew.pathingTarget?.room == room) return
        crew.setTargetRoom(room)
    }
}

class ManningTask(val room: Room) : AITask() {
    override val priority: Int = run {
        val systemPriority = when (room.system) {
            is Piloting -> 55
            is Engines -> 45
            is Weapons, is Shields, is Doors, is Sensors -> 20
            else -> error("Unmannable system ${room.system}")
        }

        systemPriority
    }

    override fun update() {
        val crew = assignee ?: return
        if (crew.pathingTarget?.room == room) return
        crew.setTargetRoom(room)
    }

    override fun equals(other: Any?): Boolean {
        return other is ManningTask && other.room == room
    }

    override fun hashCode(): Int {
        return room.hashCode()
    }
}
