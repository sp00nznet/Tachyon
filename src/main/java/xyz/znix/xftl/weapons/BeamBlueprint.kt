package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.opengl.TextureImpl
import org.newdawn.slick.opengl.renderer.Renderer
import org.newdawn.slick.opengl.renderer.SGL
import xyz.znix.xftl.FTLAnimation
import xyz.znix.xftl.Ship
import xyz.znix.xftl.drones.CombatDrone
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.systems.SelectedTarget
import kotlin.math.*
import kotlin.random.Random
import kotlin.random.nextInt

class BeamBlueprint(xml: Element) : AbstractWeaponBlueprint(xml) {
    val length: Int = xml.getChildTextTrim("length").toInt()

    // See the note on SPEED_MULTIPLIER.
    val speedPixelsPerSecond = (speed ?: DEFAULT_SPEED) * SPEED_MULTIPLIER

    val fireDuration: Float = 1f * length / speedPixelsPerSecond

    // TODO implement super-shield (Zoltan shield) support

    override fun buildInstance(ship: Ship): AbstractWeaponInstance {
        return BeamInstance(ship)
    }

    inner class BeamInstance(ship: Ship) : AbstractWeaponInstance(this, ship) {
        val firing: Boolean get() = target != null
        private var firingTime: Float = 0f
        private var target: SelectedTarget.BeamAim? = null
        private val originPos = Point(0, 0)

        // This is effectively a RoomPoint, but split up to make serialisation easier.
        private var lastPos = Point(INVALID_CELL_POS)
        private var lastRoomId: Int? = null

        private val contact: FTLAnimation

        // Copy this in as a mutable variable so it can be changed for drones.
        private var fireDuration: Float = this@BeamBlueprint.fireDuration

        // Similarly, this is used for artillery
        private var length: Int = this@BeamBlueprint.length

        init {
            // Turns out this is a one-frame animation, leave it looping in case
            // a mod replaces it or something.
            contact = ship.sys.animations[projectile!!].startLooping()
        }

        override fun render(g: Graphics) {
            if (firing) {
                // Manually compute the frames rather than using an animation, since
                // we're controlling the laser progress on the same timer.
                val progress = firingTime / fireDuration
                val firstFiringFrame = animation.chargedFrame + 1
                val firingFrames = animation.length - firstFiringFrame
                val frameNum = firstFiringFrame + (firingFrames * progress).toInt()

                animation.spriteAt(frameNum).draw()

                // Draw the beam line
                // Note that since we're in image space, forwards is up so forwards
                // is negative Y. Use some 'long enough' arbitrary length.
                val offset = animation.firePoint
                drawBeam(damage, offset, offset + ConstPoint(0, -5000))
            } else {
                super.render(g)
            }
        }

        fun renderInbound() {
            renderInbound(originPos)
        }

        fun drawDroneBeam(drone: CombatDrone) {
            if (!firing)
                return

            val fc = drone.flightController
            val droneCentre = Point(fc.position)

            // For some stupid reason, the beam doesn't come clean
            // out of the centre of the drone - it has to be offset
            // slightly.
            // For example, the Beam 2 drone is shifted one pixel
            // to the right, so we need to shift the beam origin
            // by that amount too.
            droneCentre.x += cos(fc.rotation).roundToInt()
            droneCentre.y += sin(fc.rotation).roundToInt()

            renderInbound(droneCentre)
        }

        fun drawArtilleryBeam(startPos: IPoint, destPos: IPoint) {
            if (!firing)
                return

            // Draw the beam line
            drawBeam(damage, startPos, destPos)
        }

        private fun renderInbound(from: IPoint) {
            // Draw the beam on the enemy ship, including the little contact burning animation

            // This means we don't have to put !! on every usage of target
            val target = target!!

            // Figure out where the beam touches the ship
            val targetPos = pointAtTime(firingTime)

            // Calculate the point where the beam and the shield bubble intersect
            // (Note that if the shields are off, we still do this - but the beams
            //  on each side will appear the same, so it doesn't matter)

            val targetShip = target.targetShip
            val shieldSize = targetShip.shieldHalfSize
            val shieldOrigin = targetShip.shieldOrigin

            val intersections = findIntersections(from, targetPos, shieldSize, shieldOrigin)

            // If we only cross the shield layer once, pick the first solution.
            // Note it's valid to never cross the shield line, or cross it twice,
            // if the beam leaves the shield layer.
            val iFirst = intersections.first
            val iSecond = intersections.second
            val shieldPoint: IPoint = when {
                iFirst == null -> targetPos // No attenuation
                iSecond == null -> iFirst // Crosses the bubble once, normal.

                // The beam is going into one side of the shield bubble
                // and out the other.
                // Check which is nearest, so the attenuation happens when
                // the line first crosses the shield bubble.
                iFirst.distToSq(from) < iSecond.distToSq(from) -> iFirst
                else -> iSecond
            }

            // Beams that do no damage (eg the fire beam) still have a line
            // drawn, which is the same as a one-power beam.
            val visualPower = max(1, damage)

            var shieldLayers = targetShip.shields?.activeShields ?: 0
            shieldLayers = max(0, shieldLayers - type.shieldPiercing)
            val piercing = max(0, visualPower - shieldLayers)

            // TODO make the transition around the shield line a bit cleaner - it's a clear
            //  square cutoff, and doesn't match the tangent line of the shields bubble.
            drawBeam(visualPower, from, shieldPoint)

            // Draw the inside-the-shield-bubble part
            if (piercing == 0)
                return

            drawBeam(piercing, shieldPoint, targetPos)

            // Draw the contact animation
            // FIXME hardcode the offset of the contact point relative
            //  to the image - I can't see this specified anywhere.
            contact.draw(targetPos.x - 24f, targetPos.y - 32f, BEAM_COLOUR_OPAQUE)
        }

        override fun update(dt: Float, canCharge: Boolean, isHacked: Boolean) {
            super.update(dt, canCharge, isHacked)

            if (firing) {
                val targetShip = target!!.targetShip

                // Don't block charging while firing - it does appear
                // to charge up even while firing!

                // Step across all the points the beam has moved
                // across in this frame. This is done to ensure
                // the beam can't skip rooms when running with
                // high delta-times.
                val onePixelTime = fireDuration / length
                val newFiringTime = firingTime + dt
                var t = firingTime
                val tmp = Point(0, 0)
                while (t < newFiringTime) {
                    // Check if we've potentially crossed into a new room
                    tmp.set(pointAtTime(t))
                    t += onePixelTime
                    targetShip.screenPosToShipPos(tmp)

                    if (tmp != lastPos) {
                        lastPos.set(tmp)
                        val room = targetShip.shipToRoomPos(tmp)?.room

                        // Is this point outside the ship?
                        if (room == null) {
                            lastRoomId = null
                            continue
                        }

                        var shieldLayers = targetShip.shields?.activeShields ?: 0
                        shieldLayers = max(0, shieldLayers - type.shieldPiercing)
                        val beamPower = max(0, damage - shieldLayers)

                        // If we hit a new room, damage it
                        if (lastRoomId != room.id) {
                            lastRoomId = room.id
                            targetShip.damage(room, beamPower, beamPower, 0)
                        }

                        // TODO deal crew damage - this is done on entry
                        //  to a new cell, not only to a new room.
                    }
                }

                firingTime = newFiringTime
                if (firingTime >= fireDuration) {
                    firingTime = 0f
                    target = null

                    // Reset these to make the savegame more compact
                    fireDuration = this@BeamBlueprint.fireDuration
                    length = this@BeamBlueprint.length

                    // Without this, if the first room hit was the same room as the
                    // last one hit on the previous shot, no damage would be dealt.
                    lastPos.set(INVALID_CELL_POS)
                    lastRoomId = null

                    originPos.set(ConstPoint.ZERO)

                    targetShip.inboundBeams.remove(this)
                }

                contact.update(dt)
            }
        }

        fun fire(target: SelectedTarget.BeamAim) {
            this.target = target
            target.targetShip.inboundBeams.add(this)

            type.launchSounds?.get()?.play()

            timeCharged = 0f

            // Find the point the beam is going to come from. Only do this once per
            // shot so the beam doesn't bounce around.
            // This is mostly guessed from images of FTL, and may vary quite a bit.
            // So long as it looks reasonable though that shouldn't be an issue, since
            // this is purely cosmetic.
            // It can start in any of the four corners, and apply a large amount of
            // randomisation in addition.
            originPos.set(1000, 1000)

            if (Random.nextBoolean())
                originPos.x *= -1
            if (Random.nextBoolean())
                originPos.y *= -1

            val range = 350
            originPos.x += Random.nextInt(-range..range)
            originPos.y += Random.nextInt(-range..range)

            originPos += target.targetShip.shieldOrigin
        }

        fun fireFromDrone(drone: CombatDrone, target: SelectedTarget.BeamAim, duration: Float) {
            this.target = target
            fireDuration = duration
        }

        fun fireFromArtillery(target: SelectedTarget.BeamAim, length: Int) {
            fire(target)
            this.length = length
        }

        // For use by drones, so they can angle themselves correctly.
        fun getCurrentTargetPoint(): IPoint {
            if (!firing)
                return ConstPoint.ZERO

            return pointAtTime(firingTime)
        }

        private fun pointAtTime(time: Float): IPoint {
            val distanceAcross = (length * time / fireDuration).toInt()

            return target!!.startShipPoint + ConstPoint(
                (distanceAcross * cos(target!!.angle)).toInt(),
                (distanceAcross * sin(target!!.angle)).toInt()
            )
        }

        override fun saveToXML(elem: Element, refs: ObjectRefs) {
            super.saveToXML(elem, refs)

            SaveUtil.addTagFloat(elem, "firingTime", firingTime, 0f)
            SaveUtil.addTagFloat(elem, "fireDuration", fireDuration, this@BeamBlueprint.fireDuration)
            SaveUtil.addTagInt(elem, "length", length, this@BeamBlueprint.length)

            if (target != null) {
                val targetElem = Element("target")
                target!!.saveToXML(targetElem, refs)
                elem.addContent(targetElem)
            }

            if (!(originPos posEq ConstPoint.ZERO)) {
                SaveUtil.addPoint(elem, "originPos", originPos)
            }

            if (!(lastPos posEq INVALID_CELL_POS)) {
                SaveUtil.addPoint(elem, "lastPos", lastPos)
            }
            SaveUtil.addTagInt(elem, "lastRoomId", lastRoomId, null)
        }

        override fun loadFromXML(elem: Element, refs: RefLoader) {
            super.loadFromXML(elem, refs)

            firingTime = SaveUtil.getOptionalTagFloat(elem, "firingTime") ?: 0f
            fireDuration = SaveUtil.getOptionalTagFloat(elem, "fireDuration") ?: this@BeamBlueprint.fireDuration
            length = SaveUtil.getOptionalTagInt(elem, "length") ?: this@BeamBlueprint.length

            val targetElem = elem.getChild("target")
            if (targetElem != null) {
                SelectedTarget.loadFromXML(targetElem, refs, { this }) { target ->
                    this.target = target as SelectedTarget.BeamAim

                    // Without this, the beam graphic won't render on the target ship.
                    // Note this only runs if the target is non-null.
                    target.targetShip.inboundBeams.add(this)
                }
            }

            if (elem.getChild("originPos") != null) {
                originPos.set(SaveUtil.getPoint(elem, "originPos"))
            }

            if (elem.getChild("lastPos") != null) {
                lastPos.set(SaveUtil.getPoint(elem, "lastPos"))
            }
            lastRoomId = SaveUtil.getOptionalTagInt(elem, "lastRoomId")
        }
    }

    private fun drawBeam(power: Int, src: IPoint, dst: IPoint) {
        // The beam is drawn as a transparent-red-transparent gradient line
        // Find the tangent line of src-dst, and use that to find the edges of the beam
        val tangent = FPos(-(src.y - dst.y).f, (src.x - dst.x).f)
        val length = sqrt(tangent.x * tangent.x + tangent.y * tangent.y)
        tangent.x /= length
        tangent.y /= length

        // Scale up the tangent vector based on the per-side width
        val width = 3 * power
        tangent.x *= width
        tangent.y *= width

        val srcA = FPos(src.x + tangent.x, src.y + tangent.y)
        val srcB = FPos(src.x - tangent.x, src.y - tangent.y)

        val dstA = FPos(dst.x + tangent.x, dst.y + tangent.y)
        val dstB = FPos(dst.x - tangent.x, dst.y - tangent.y)

        drawGradient(srcA, srcB, dstA, dstB, BEAM_COLOUR_TRANSPARENT)
    }

    /**
     * Draw a quad that's opaque along a centre line, and fades to the edge.
     */
    private fun drawGradient(
        srcA: FPos, srcB: FPos,
        dstA: FPos, dstB: FPos,
        colour: Color
    ) {
        TextureImpl.bindNone()

        // Find the middle points where the colour should be strongest
        val srcMidX = (srcA.x + srcB.x) / 2
        val srcMidY = (srcA.y + srcB.y) / 2

        val dstMidX = (dstA.x + dstB.x) / 2
        val dstMidY = (dstA.y + dstB.y) / 2

        // Fade to transparent, but without blending to another colour in the process.
        val edge = Color(colour)
        edge.a = 0f

        val gl: SGL = Renderer.get()

        gl.glBegin(SGL.GL_QUADS)

        // Draw the A-side - srcA,srcMid,dstA,dstMid
        edge.bind()
        gl.glVertex2f(srcA.x, srcA.y)
        gl.glVertex2f(dstA.x, dstA.y)
        colour.bind()
        gl.glVertex2f(dstMidX, dstMidY)
        gl.glVertex2f(srcMidX, srcMidY)

        // Draw the B-side - srcB,srcMid,dstB,dstMid
        colour.bind()
        gl.glVertex2f(dstMidX, dstMidY)
        gl.glVertex2f(srcMidX, srcMidY)
        edge.bind()
        gl.glVertex2f(srcB.x, srcB.y)
        gl.glVertex2f(dstB.x, dstB.y)

        gl.glEnd()
    }

    private fun findIntersections(src: IPoint, dst: IPoint, ellipse: IPoint, centre: IPoint): Pair<IPoint?, IPoint?> {
        // Solve the line-ellipse intersection - find the point that satisfies
        // both the ellipse and line equation, where w and h are the half-width
        // and half-height:
        //
        // x^2/w^2 + y^2/h^2 = 1   (ellipse equation)
        // y=mx+L                  (line equation, using L not c to free it up later)
        //
        // x^2/w^2 + (mx+L)^2/h^2 = 1
        // x^2/w^2 + ((mx)^2 + 2mxL + L^2)/h^2 = 1
        // x^2/w^2 + (mx)^2/h^2 + 2mxL/h^2 + L^2/h^2 - 1 = 0
        // x^2(1/w^2 + m^2/h^2) + 2mxL/h^2 + (L^2/h^2 - 1) = 0
        //
        // ax^2 + bx + c = 0 where:
        //   a = 1/w^2 + m^2/h^2
        //   b = 2mL/h^2
        //   c = L^2/h^2 - 1
        //
        // This is now a quadratic equation, solve it using the quadratic formula.

        // Find the line equation
        val deltaX = dst.x.f - src.x
        val deltaY = dst.y.f - src.y

        // Handle the special-case for vertical lines, since we otherwise
        // divide through by zero. This doesn't result in a crash, instead
        // the beam appears to pass through the shields as everything becomes
        // either NaN or infinity.
        // This can occur with beam drones, and became very quickly obvious
        // once they were first implemented, as it occurred surprisingly often!
        if (abs(deltaX) < 0.001f) {
            // Move the ellipse to the centre
            val x = src.x - centre.x

            // Outside the ellipse's area?
            if (x !in -ellipse.x..ellipse.x)
                return Pair(null, null)

            // Find the y coordinate of the shield at the given x point.
            // (there's another point on the shield line by negating this y)

            val shieldY = (ellipse.y * sqrt(1 - x.f.pow(2) / ellipse.x.f.pow(2))).toInt()

            fun yToPoint(y: Int): IPoint? {
                // Check this point sits inside the line segment.
                val minY = min(src.y, dst.y)
                val maxY = max(src.y, dst.y)
                if (y !in minY..maxY)
                    return null

                return ConstPoint(src.x, y)
            }

            val p1 = yToPoint(centre.y + shieldY)
            val p2 = yToPoint(centre.y - shieldY)

            if (p1 == null) {
                return Pair(p2, null)
            }
            return Pair(p1, p2)
        }

        val m = deltaY / deltaX // Rise over run

        // This is where we subtract out the centre so this is valid when the ellipse
        // is centred at the origin.
        @Suppress("LocalVariableName")
        val L = (src.y - centre.y) - (src.x - centre.x) * m

        val wSq = ellipse.x.f.pow(2)
        val hSq = ellipse.y.f.pow(2)
        val a = 1 / wSq + m.pow(2) / hSq
        val b = 2 * m * L / hSq
        val c = L.pow(2) / hSq - 1

        // Check if the determinant is negative, which implies complex answers
        // (AKA the beam missed the shield)
        val determinant = b.pow(2) - 4 * a * c
        if (determinant < 0)
            return Pair(null, null)

        fun xToPoint(x: Float): IPoint? {
            // Check this point sits inside the line segment.
            val minX = min(src.x, dst.x)
            val maxX = max(src.x, dst.x)
            if (x.toInt() + centre.x !in minX..maxX)
                return null

            val y = m * x + L
            return ConstPoint(x.toInt() + centre.x, y.toInt() + centre.y)
        }

        // Check if there's only one solution (the line is a tangent line to
        // a point on the shield)
        if (determinant == 0f)
            return Pair(xToPoint(-b / (2 * a)), null)

        val plusMinus = sqrt(determinant)

        val p1 = xToPoint((-b + plusMinus) / (2 * a))
        val p2 = xToPoint((-b - plusMinus) / (2 * a))

        // If we have a non-null solution, always return it as the first argument
        return if (p1 == null) Pair(p2, null) else Pair(p1, p2)
    }

    // Quick-and-dirty floating point vector
    private class FPos(var x: Float, var y: Float)

    companion object {
        // Speed defaults to 5 according to:
        // https://ftl.fandom.com/wiki/Beam_(Weapons)#Beam_weapons_table
        // (By matching the speed of something like the Halbard beam (BEAM_2) with the XML)
        // This obviously isn't in pixels per second - the mini beam
        // has a speed of 3 and a length of 45, and it certainly doesn't
        // take 15 seconds to fire. Playing a YouTube video of someone
        // using Stealth A and firing a mini beam without pausing, and
        // using MPV to get the exact timestamps shows (to within a couple
        // frames at 60fps) a time of 0.933, another one with a Halbard
        // beam on Zoltan A at 0.966 (Billy1Kirby's speedruns are the source
        // videos I used, as a note).
        // This suggests that multiplying the speed by around 16.5 gives
        // us a pixel-per-second speed, though further research (either
        // disassembly or modifying the values in vanilla FTL) may be
        // warranted for high accuracy.
        private const val SPEED_MULTIPLIER = 16.5f

        // The default speed, in the same units as the XML.
        private const val DEFAULT_SPEED = 5

        // A bright red and not completely opaque beam? This is largely guessed.
        private val BEAM_COLOUR_TRANSPARENT = Color(255, 30, 30, 220)
        private val BEAM_COLOUR_OPAQUE = Color(BEAM_COLOUR_TRANSPARENT).apply { a = 1f }

        private val INVALID_CELL_POS = ConstPoint(-999, -999)
    }
}
