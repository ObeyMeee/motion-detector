package ua.com.andromeda.motiondetector.utils

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.roundToInt

object BackflipDetector {
    fun detect(
        landmarkSequences: List<List<NormalizedLandmark>>,
        originalFrameIndicies: List<Int>,
        frameRate: Double
    ): List<Int> {
        var stage = 0
        val flipStarts = mutableListOf<Int>()
        val flipStartsTemp = mutableListOf<Int>()
        val jumpMoments = mutableListOf<Int>()
        val finalJumpMoments = mutableListOf<Int>()
        var skipToIndex: Int? = null
        val minInterval = (5 / 30f * frameRate).toInt()
        val maxInterval = (1.0 * frameRate).roundToInt()

        for (i in landmarkSequences.indices) {
            if (skipToIndex != null && i < skipToIndex) continue

            for (j in (i..<landmarkSequences.size)) {
                val landmarks = landmarkSequences[j]

                val leftShoulder = landmarks[PoseLandmark.LEFT_SHOULDER]
                val rightShoulder = landmarks[PoseLandmark.RIGHT_SHOULDER]
                val leftWrist = landmarks[PoseLandmark.RIGHT_WRIST]
                val rightWrist = landmarks[PoseLandmark.RIGHT_WRIST]
                val leftHip = landmarks[PoseLandmark.LEFT_HIP]
                val rightHip = landmarks[PoseLandmark.RIGHT_HIP]
                val leftKnee = landmarks[PoseLandmark.LEFT_KNEE]
                val rightKnee = landmarks[PoseLandmark.RIGHT_KNEE]
                val leftAnkle = landmarks[PoseLandmark.LEFT_ANKLE]
                val rightAnkle = landmarks[PoseLandmark.RIGHT_ANKLE]

                val leftArmAngle = AngleCalculator.calculate(leftWrist, leftShoulder, leftHip)
                val rightArmAngle = AngleCalculator.calculate(rightWrist, rightShoulder, rightHip)

                val leftLegAngle = AngleCalculator.calculate(leftHip, leftKnee, leftAnkle)
                val rightLegAngle = AngleCalculator.calculate(rightHip, rightKnee, rightAnkle)

                val leftTorsoAngle = AngleCalculator.calculate(leftShoulder, leftHip, leftKnee)
                val rightTorsoAngle = AngleCalculator.calculate(rightShoulder, rightHip, rightKnee)

                when (stage) {
                    0 -> {
                        jumpMoments.clear()
                        if (leftArmAngle < 110 && rightArmAngle < 110 &&
                            leftTorsoAngle > 150 && rightTorsoAngle > 150 &&
                            leftLegAngle > 150 && rightLegAngle > 150 &&
                            leftWrist.x() > leftShoulder.x() && rightWrist.x() > rightShoulder.x() &&
                            leftHip.y() < leftKnee.y() && rightHip.y() < rightKnee.y()
                        ) {
                            stage = 1
                            flipStartsTemp.add(j)
                        }
                    }

                    1 -> {
                        if (leftArmAngle < 100 && rightArmAngle < 100 &&
                            leftLegAngle < 130 && rightLegAngle < 130 &&
                            leftTorsoAngle < 140 && rightTorsoAngle < 140
                        ) {
                            stage = 2
                        } else if (leftArmAngle < 110 && rightArmAngle < 110 &&
                            leftLegAngle > 160 && rightLegAngle > 160 &&
                            leftTorsoAngle > 150 && rightTorsoAngle > 150 &&
                            leftWrist.x() > leftShoulder.x() && rightWrist.x() > rightShoulder.x() &&
                            leftHip.y() < leftKnee.y() && rightHip.y() < rightKnee.y()
                        ) {
                            if (flipStartsTemp.isNotEmpty() && (j - flipStartsTemp.last()) > minInterval) {
                                stage = 0
                                flipStartsTemp.removeAt(flipStartsTemp.size - 1)
                            }
                        }
                    }

                    2 -> {
                        if (j >= minInterval) {
                            val leftFootDiff =
                                landmarkSequences[j - minInterval][PoseLandmark.LEFT_FOOT_INDEX].y() - landmarks[PoseLandmark.LEFT_FOOT_INDEX].y()
                            val rightFootDiff =
                                landmarkSequences[j - minInterval][PoseLandmark.RIGHT_FOOT_INDEX].y() - landmarks[PoseLandmark.RIGHT_FOOT_INDEX].y()
                            val leftFootDistance =
                                abs(landmarks[PoseLandmark.LEFT_ANKLE].y() - landmarks[PoseLandmark.LEFT_KNEE].y())
                            val rightFootDistance =
                                abs(landmarks[PoseLandmark.RIGHT_ANKLE].y() - landmarks[PoseLandmark.RIGHT_KNEE].y())

                            if (leftFootDiff > leftFootDistance && rightFootDiff > leftFootDistance &&
                                leftFootDiff > rightFootDistance && rightFootDiff > rightFootDistance
                            ) {
                                jumpMoments.add(j - minInterval)
                            }
                        }
                        if (leftAnkle.y() < leftHip.y() && rightAnkle.y() < rightHip.y()) {
                            if (flipStartsTemp.isNotEmpty() && (j - flipStartsTemp.last()) < maxInterval) {
                                stage = 0
                                flipStarts.add(originalFrameIndicies[flipStartsTemp.last()])
                                if (jumpMoments.isNotEmpty()) {
                                    finalJumpMoments.add(originalFrameIndicies[jumpMoments.first()])
                                }
                                skipToIndex = j + 1
                                break
                            } else {
                                stage = 0
                                skipToIndex = flipStartsTemp.last() + 1
                                break
                            }
                        }
                    }
                }
                if (j == landmarkSequences.lastIndex) {
                    skipToIndex = j
                    break
                }
            }
        }
        return finalJumpMoments
    }

}