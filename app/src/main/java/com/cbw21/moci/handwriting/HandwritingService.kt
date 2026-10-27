package com.cbw21.moci.handwriting

import com.cbw21.moci.data.HandwritingLanguage
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class InkPoint(val x: Float, val y: Float, val timestamp: Long)

data class InkLine(val points: List<InkPoint>)

data class RecognizedCandidate(val text: String, val score: Double)

object HandwritingService {
    private const val MAX_RESULTS = 8

    private val models = mutableMapOf<HandwritingLanguage, DigitalInkRecognitionModel>()
    private val recognizers = mutableMapOf<HandwritingLanguage, DigitalInkRecognizer>()

    fun model(language: HandwritingLanguage): DigitalInkRecognitionModel =
        models.getOrPut(language) {
            val identifier = when (language) {
                HandwritingLanguage.English -> DigitalInkRecognitionModelIdentifier.EN_US
                HandwritingLanguage.Chinese -> DigitalInkRecognitionModelIdentifier.ZH_HANI_CN
            }
            DigitalInkRecognitionModel.builder(identifier).build()
        }

    suspend fun isModelDownloaded(language: HandwritingLanguage): Boolean {
        return RemoteModelManager.getInstance().isModelDownloaded(model(language)).await()
    }

    suspend fun downloadModel(language: HandwritingLanguage) {
        val conditions = DownloadConditions.Builder().build()
        RemoteModelManager.getInstance().download(model(language), conditions).await()
    }

    suspend fun recognize(
        language: HandwritingLanguage,
        lines: List<InkLine>,
    ): List<RecognizedCandidate> {
        if (lines.isEmpty()) return emptyList()

        val inkBuilder = Ink.builder()
        for (line in lines) {
            if (line.points.isEmpty()) continue
            val strokeBuilder = Ink.Stroke.builder()
            for (point in line.points) {
                strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.timestamp))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }

        val recognizer = recognizers.getOrPut(language) {
            DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model(language))
                    .setMaxResultCount(MAX_RESULTS)
                    .build(),
            )
        }
        val result = recognizer.recognize(inkBuilder.build()).await()
        return result.candidates.map { candidate ->
            RecognizedCandidate(
                text = candidate.text,
                score = candidate.score?.toDouble() ?: 0.0,
            )
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> continuation.resume(value) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }
}
