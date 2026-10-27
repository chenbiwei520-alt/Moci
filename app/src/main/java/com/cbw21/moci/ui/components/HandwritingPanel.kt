package com.cbw21.moci.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cbw21.moci.data.HandwritingLanguage
import com.cbw21.moci.handwriting.HandwritingService
import com.cbw21.moci.handwriting.InkLine
import com.cbw21.moci.handwriting.InkPoint
import com.cbw21.moci.handwriting.RecognizedCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val AUTO_RECOGNIZE_DELAY_MS = 700L

private val EraserIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Eraser",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(15.14f, 3f)
            curveTo(14.63f, 3f, 14.12f, 3.2f, 13.73f, 3.59f)
            lineTo(2.59f, 14.73f)
            curveTo(1.81f, 15.51f, 1.81f, 16.78f, 2.59f, 17.56f)
            lineTo(5.03f, 20f)
            lineTo(12.17f, 20f)
            lineTo(20.41f, 11.76f)
            curveTo(21.19f, 10.98f, 21.19f, 9.71f, 20.41f, 8.93f)
            lineTo(16.55f, 5.07f)
            curveTo(16.16f, 3.2f, 15.65f, 3f, 15.14f, 3f)
            close()
            moveTo(5.03f, 18f)
            lineTo(3.26f, 16.23f)
            lineTo(8.11f, 11.38f)
            lineTo(11.65f, 14.92f)
            lineTo(8.79f, 18f)
            lineTo(5.03f, 18f)
            close()
        }
    }.build()
}

private sealed interface ModelState {
    data object Checking : ModelState
    data object Missing : ModelState
    data object Downloading : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

private enum class PadTool { Pen, Eraser }

@Composable
fun HandwritingPanel(
    language: HandwritingLanguage,
    wordKey: Int,
    answer: String,
    onAnswer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var modelState by remember(language) { mutableStateOf<ModelState>(ModelState.Checking) }
    var retryKey by remember(language) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(language, retryKey) {
        modelState = try {
            if (HandwritingService.isModelDownloaded(language)) {
                ModelState.Ready
            } else {
                ModelState.Missing
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ModelState.Failed("无法检测手写识别模型，请确认设备已安装并登录 Google 服务。")
        }
    }

    val downloadModel = {
        if (modelState !is ModelState.Downloading) {
            modelState = ModelState.Downloading
            scope.launch {
                try {
                    HandwritingService.downloadModel(language)
                    modelState = ModelState.Ready
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    modelState = ModelState.Failed("模型下载失败，请检查网络后重试。")
                }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "手写默写", style = MiuixTheme.textStyles.title4)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (language == HandwritingLanguage.English) "英文识别" else "中文识别",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(14.dp))

        when (val state = modelState) {
            ModelState.Checking -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            ModelState.Missing -> {
                ModelPrompt(
                    onDownload = downloadModel,
                )
            }

            ModelState.Downloading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(size = 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "正在下载手写模型…",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            is ModelState.Failed -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.message,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(text = "重新检测", onClick = { retryKey++ })
                        Button(onClick = downloadModel) { Text("下载模型") }
                    }
                }
            }

            ModelState.Ready -> {
                key(wordKey) {
                    HandwritingPad(
                        language = language,
                        answer = answer,
                        onAnswer = onAnswer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelPrompt(onDownload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = MiuixIcons.Edit,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "首次使用需下载离线手写模型",
            style = MiuixTheme.textStyles.body1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "约 20 MB，仅需下载一次；识别在设备本地完成。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onDownload,
            colors = ButtonDefaults.buttonColorsPrimary(),
            insideMargin = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
        ) {
            Text("下载手写模型")
        }
    }
}

@Composable
private fun HandwritingPad(
    language: HandwritingLanguage,
    answer: String,
    onAnswer: (String) -> Unit,
) {
    val pad = remember { InkPadState() }
    val scope = rememberCoroutineScope()
    var recognizing by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<RecognizedCandidate>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var tool by remember { mutableStateOf(PadTool.Pen) }
    var inkVersion by remember { mutableIntStateOf(0) }
    var autoJob by remember { mutableStateOf<Job?>(null) }
    var recognizeJob by remember { mutableStateOf<Job?>(null) }
    val recognitionTicket = remember { intArrayOf(0) }
    val eraseRadius = with(LocalDensity.current) { 18.dp.toPx() }
    val scheme = MiuixTheme.colorScheme
    val inkColor = scheme.onSurface

    fun startRecognition(automatic: Boolean) {
        if (pad.strokes.isEmpty()) return
        val version = inkVersion
        val ticket = ++recognitionTicket[0]
        recognizeJob?.cancel()
        recognizeJob = scope.launch {
            recognizing = true
            if (!automatic) error = null
            try {
                val result = HandwritingService.recognize(language, pad.toLines())
                if (version != inkVersion || ticket != recognitionTicket[0]) return@launch
                candidates = result
                val best = result.firstOrNull()?.text?.trim().orEmpty()
                if (best.isNotEmpty()) {
                    error = null
                    onAnswer(best)
                } else if (!automatic) {
                    error = "没有识别出文字，请重写后重试。"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == inkVersion && ticket == recognitionTicket[0] && !automatic) {
                    error = "识别失败，请重试。"
                }
            } finally {
                if (version == inkVersion && ticket == recognitionTicket[0]) {
                    recognizing = false
                }
            }
        }
    }

    fun scheduleAutoRecognition() {
        autoJob?.cancel()
        autoJob = scope.launch {
            delay(AUTO_RECOGNIZE_DELAY_MS)
            startRecognition(automatic = true)
        }
    }

    fun applyCandidate(text: String) {
        recognizeJob?.cancel()
        recognitionTicket[0]++
        recognizing = false
        error = null
        onAnswer(text)
    }

    LaunchedEffect(inkVersion) {
        if (inkVersion == 0) return@LaunchedEffect
        autoJob?.cancel()
        recognizeJob?.cancel()
        recognitionTicket[0]++
        recognizing = false
        candidates = emptyList()
        error = null
        if (pad.strokes.isEmpty()) {
            if (answer.isNotBlank()) onAnswer("")
        } else {
            scheduleAutoRecognition()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceContainerHighest),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tool, eraseRadius) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val changed = if (tool == PadTool.Eraser) {
                                val gesture = pad.beginErase()
                                gesture.eraseAt(down.position.x, down.position.y, eraseRadius)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    if (change.positionChanged()) {
                                        gesture.eraseAt(
                                            change.position.x,
                                            change.position.y,
                                            eraseRadius,
                                        )
                                        change.consume()
                                    }
                                }
                                gesture.finish()
                            } else {
                                pad.begin(down.position.x, down.position.y, down.uptimeMillis)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    if (change.positionChanged()) {
                                        pad.append(
                                            change.position.x,
                                            change.position.y,
                                            change.uptimeMillis,
                                        )
                                        change.consume()
                                    }
                                }
                                true
                            }
                            if (changed) inkVersion++
                        }
                    },
            ) {
                val strokeWidth = 2.5.dp.toPx()
                pad.strokes.forEach { stroke ->
                    val points = stroke.points
                    when {
                        points.isEmpty() -> Unit
                        points.size == 1 -> drawCircle(
                            color = inkColor,
                            radius = strokeWidth,
                            center = Offset(points[0].x, points[0].y),
                        )

                        else -> {
                            val path = Path()
                            path.moveTo(points[0].x, points[0].y)
                            for (index in 1 until points.size) {
                                path.lineTo(points[index].x, points[index].y)
                            }
                            drawPath(
                                path = path,
                                color = inkColor,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }
                    }
                }
            }
            if (pad.strokes.isEmpty()) {
                Text(
                    text = if (tool == PadTool.Eraser) {
                        "橡皮擦模式：在笔画上滑动擦除"
                    } else {
                        "用触控笔或手指在此书写，停笔后自动识别"
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 20.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = scheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (pad.undo()) inkVersion++
                },
                enabled = pad.canUndo(),
            ) {
                Icon(
                    imageVector = MiuixIcons.Undo,
                    contentDescription = "撤销",
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(
                onClick = {
                    if (pad.strokes.isNotEmpty()) {
                        pad.clear()
                        inkVersion++
                    }
                },
                enabled = pad.strokes.isNotEmpty(),
            ) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = "清空书写",
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(
                onClick = {
                    tool = if (tool == PadTool.Eraser) PadTool.Pen else PadTool.Eraser
                },
            ) {
                Icon(
                    imageVector = EraserIcon,
                    contentDescription = if (tool == PadTool.Eraser) "结束擦除" else "橡皮擦",
                    modifier = Modifier.size(22.dp),
                    tint = if (tool == PadTool.Eraser) scheme.primary else scheme.onSurface,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    autoJob?.cancel()
                    autoJob = null
                    startRecognition(automatic = false)
                },
                enabled = pad.strokes.isNotEmpty() && !recognizing,
                colors = ButtonDefaults.buttonColorsPrimary(),
                insideMargin = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
            ) {
                Text(if (recognizing) "识别中…" else "识别")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "当前答案",
                style = MiuixTheme.textStyles.footnote1,
                color = scheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = answer.ifBlank { "尚未识别" },
                style = MiuixTheme.textStyles.body2,
                color = if (answer.isBlank()) scheme.onSurfaceVariantSummary else scheme.primary,
            )
            Spacer(Modifier.weight(1f))
            if (recognizing) {
                CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "自动识别中",
                    style = MiuixTheme.textStyles.footnote1,
                    color = scheme.onSurfaceVariantSummary,
                )
            }
        }

        if (candidates.isNotEmpty()) {
            Text(
                text = "识别候选",
                modifier = Modifier.padding(top = 10.dp),
                style = MiuixTheme.textStyles.footnote1,
                color = scheme.onSurfaceVariantSummary,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(candidates.take(8)) { candidate ->
                    val text = candidate.text.trim()
                    if (text.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.surfaceContainerHigh)
                                .clickable { applyCandidate(text) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = text,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
            }
        }

        error?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(top = 6.dp),
                style = MiuixTheme.textStyles.footnote1,
                color = scheme.error,
            )
        }
    }
}

private class InkStroke(val points: SnapshotStateList<InkPoint>) {
    fun isNear(x: Float, y: Float, radiusSquared: Float): Boolean {
        if (points.isEmpty()) return false
        if (points.size == 1) {
            val dx = x - points[0].x
            val dy = y - points[0].y
            return dx * dx + dy * dy <= radiusSquared
        }
        for (index in 1 until points.size) {
            if (distanceSquaredToSegment(x, y, points[index - 1], points[index]) <= radiusSquared) {
                return true
            }
        }
        return false
    }
}

private fun distanceSquaredToSegment(x: Float, y: Float, a: InkPoint, b: InkPoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (dx == 0f && dy == 0f) {
        val ex = x - a.x
        val ey = y - a.y
        return ex * ex + ey * ey
    }
    val t = (((x - a.x) * dx + (y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    val ex = x - (a.x + t * dx)
    val ey = y - (a.y + t * dy)
    return ex * ex + ey * ey
}

private sealed interface InkEdit {
    data class Add(val stroke: InkStroke) : InkEdit
    data class Erase(val entries: List<Pair<Int, InkStroke>>) : InkEdit
}

private class EraseGesture(private val pad: InkPadState) {
    private val snapshot = pad.strokes.toList()
    private val removed = mutableListOf<InkStroke>()

    fun eraseAt(x: Float, y: Float, radius: Float) {
        val radiusSquared = radius * radius
        val hits = pad.strokes.indices.filter { index ->
            pad.strokes[index].isNear(x, y, radiusSquared)
        }
        if (hits.isEmpty()) return
        for (index in hits.sortedDescending()) {
            removed += pad.removeStrokeAt(index)
        }
    }

    fun finish(): Boolean {
        if (removed.isEmpty()) return false
        val entries = removed.mapNotNull { stroke ->
            val index = snapshot.indexOfFirst { it === stroke }
            if (index >= 0) index to stroke else null
        }
        pad.recordErase(entries)
        return true
    }
}

private class InkPadState {
    val strokes = mutableStateListOf<InkStroke>()
    private val history = mutableListOf<InkEdit>()

    fun begin(x: Float, y: Float, timestamp: Long) {
        val stroke = InkStroke(mutableStateListOf(InkPoint(x, y, timestamp)))
        strokes += stroke
        history += InkEdit.Add(stroke)
    }

    fun append(x: Float, y: Float, timestamp: Long) {
        strokes.lastOrNull()?.points?.add(InkPoint(x, y, timestamp))
    }

    fun canUndo(): Boolean = history.isNotEmpty()

    fun undo(): Boolean {
        when (val edit = history.removeLastOrNull() ?: return false) {
            is InkEdit.Add -> {
                val index = strokes.indexOfFirst { it === edit.stroke }
                if (index >= 0) strokes.removeAt(index)
            }

            is InkEdit.Erase -> {
                for ((index, stroke) in edit.entries.sortedBy { it.first }) {
                    strokes.add(index.coerceIn(0, strokes.size), stroke)
                }
            }
        }
        return true
    }

    fun clear() {
        strokes.clear()
        history.clear()
    }

    fun beginErase(): EraseGesture = EraseGesture(this)

    fun removeStrokeAt(index: Int): InkStroke = strokes.removeAt(index)

    fun recordErase(entries: List<Pair<Int, InkStroke>>) {
        if (entries.isNotEmpty()) history += InkEdit.Erase(entries)
    }

    fun toLines(): List<InkLine> = strokes
        .filter { it.points.isNotEmpty() }
        .map { InkLine(it.points.toList()) }
}
