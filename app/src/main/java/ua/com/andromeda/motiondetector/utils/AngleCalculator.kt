package ua.com.andromeda.motiondetector.utils

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.acos
import kotlin.math.sqrt

object AngleCalculator {
    fun calculate(
        a: NormalizedLandmark,
        b: NormalizedLandmark,
        c: NormalizedLandmark
    ): Double {
        val ba = arrayOf(a.x() - b.x(), a.y() - b.y())
        val bc = arrayOf(c.x() - b.x(), c.y() - b.y())
        val dotProduct = ba[0] * bc[0] + ba[1] * bc[1]
        val magnitudeBA = sqrt(ba[0] * ba[0] + ba[1] * ba[1])
        val magnitudeBC = sqrt(bc[0] * bc[0] + bc[1] * bc[1])
        val cosineAngle = dotProduct / (magnitudeBA * magnitudeBC)
        val angle = acos(cosineAngle).toDouble()
        return Math.toDegrees(angle)
    }
}
