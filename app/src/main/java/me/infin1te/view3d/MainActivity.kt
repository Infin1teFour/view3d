package me.infin1te.view3d

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.createEnvironment
import io.github.sceneview.math.Color
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.Node
import io.github.sceneview.node.MeshNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.model.model
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.model.ModelInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.infin1te.view3d.ui.theme.View3dTheme
import java.io.File
import kotlin.math.roundToInt

private const val SUPPORTED_FILE_TYPES = "Supported: .glb, .gltf, .stl, .obj"
private const val UNSUPPORTED_FILE_TYPES = "Other formats are not supported yet."
private val PICKER_MIME_TYPES = arrayOf(
    "model/*",
    "application/sla"
)

private data class TransformState(
    val tx: Float = 0f,
    val ty: Float = 0f,
    val tz: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f,
    val rz: Float = 0f,
    val scale: Float = 1f,
)

private enum class ControlPanelPage {
    Main,
    Translate,
    Rotate,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            View3dTheme {
                ModelViewerScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun ModelViewerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var loadedAsset by remember { mutableStateOf<ImportedAsset?>(null) }
    var transformState by remember { mutableStateOf(TransformState()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("Loading...") }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            try {
                isLoading = true
                loadingMessage = "Copying file..."
                loadingProgress = 0.15f
                val importedFile = withContext(Dispatchers.IO) {
                    copySelectedModelToCache(context, uri)
                }
                val displayName = resolveDisplayName(context, uri) ?: importedFile.name
                loadingMessage = "Parsing model..."
                loadingProgress = 0.6f
                val asset = withContext(Dispatchers.IO) {
                    loadImportedAsset(importedFile, displayName)
                }
                loadedAsset = asset
                transformState = TransformState()
                loadingMessage = "Preparing scene..."
                loadingProgress = 0.9f
                snackbarHostState.showSnackbar("Loaded ${asset.displayName}")
            } catch (exception: Exception) {
                loadedAsset = null
                isLoading = false
                snackbarHostState.showSnackbar(
                    exception.message ?: "Failed to load the selected file."
                )
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ModelViewport(
                loadedAsset = loadedAsset,
                transformState = transformState,
                modifier = Modifier.fillMaxSize(),
                onModelLoadFailure = { message ->
                    isLoading = false
                    scope.launch {
                        snackbarHostState.showSnackbar(message)
                    }
                },
                onLoadStateChanged = { loading, message, progress ->
                    isLoading = loading
                    message?.let { loadingMessage = it }
                    progress?.let { loadingProgress = it }
                }
            )

            if (loadedAsset != null) {
                if (showControls) {
                    TransformControls(
                        state = transformState,
                        onStateChange = { transformState = it },
                        onHide = { showControls = false },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .widthIn(max = 320.dp)
                    )
                } else {
                    FilledTonalButton(
                            onClick = { showControls = true },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        ) {
                            Text("Sterowanie")
                        }
                }
            }

            if (loadedAsset == null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .widthIn(max = 420.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "No file loaded",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Open a local GLB, GLTF, STL, or OBJ file. Swipe to orbit; pinch to zoom.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = SUPPORTED_FILE_TYPES,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = UNSUPPORTED_FILE_TYPES,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { picker.launch(PICKER_MIME_TYPES) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("Załaduj")
            }

            if (isLoading) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .widthIn(max = 340.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = loadingMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformControls(
    state: TransformState,
    onStateChange: (TransformState) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var panelPage by remember { mutableStateOf(ControlPanelPage.Main) }
    var popupOffset by remember { mutableStateOf(IntOffset(24, 24)) }

    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopStart,
        offset = popupOffset,
        onDismissRequest = onHide,
        properties = androidx.compose.ui.window.PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        )
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
            )
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .widthIn(min = 260.dp, max = 320.dp)
                    .verticalScroll(scrollState)
                    .padding(12.dp)
            ) {
                when (panelPage) {
                    ControlPanelPage.Main -> {
                        Row(
                            modifier = Modifier.pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    popupOffset = IntOffset(
                                        popupOffset.x + dragAmount.x.roundToInt(),
                                        popupOffset.y + dragAmount.y.roundToInt()
                                    )
                                }
                            }
                        ) {
                            Text("Sterowanie obiektem", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.weight(1f))
                            FilledTonalButton(onClick = onHide) {
                                Text("Zamknij")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { panelPage = ControlPanelPage.Translate },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Przesuń")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { panelPage = ControlPanelPage.Rotate },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Obróć")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SliderRow("Skala", state.scale, 0.2f..5f) { onStateChange(state.copy(scale = it)) }
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(onClick = { onStateChange(TransformState()) }) {
                            Text("Reset")
                        }
                    }

                    ControlPanelPage.Translate -> {
                        Row {
                            FilledTonalButton(onClick = { panelPage = ControlPanelPage.Main }) {
                                Text("Wstecz")
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text("Przesuwanie", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SliderRow("X", state.tx, -2f..2f) { onStateChange(state.copy(tx = it)) }
                        SliderRow("Y", state.ty, -2f..2f) { onStateChange(state.copy(ty = it)) }
                        SliderRow("Z", state.tz, -2f..2f) { onStateChange(state.copy(tz = it)) }
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { onStateChange(state.copy(tx = 0f, ty = 0f, tz = 0f)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Wyśrodkuj")
                        }
                    }

                    ControlPanelPage.Rotate -> {
                        Row {
                            FilledTonalButton(onClick = { panelPage = ControlPanelPage.Main }) {
                                Text("Wstecz")
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text("Obracanie", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SliderRow("Oś X", state.rx, -180f..180f) { onStateChange(state.copy(rx = it)) }
                        SliderRow("Oś Y", state.ry, -180f..180f) { onStateChange(state.copy(ry = it)) }
                        SliderRow("Oś Z", state.rz, -180f..180f) { onStateChange(state.copy(rz = it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
private fun ModelViewport(
    loadedAsset: ImportedAsset?,
    transformState: TransformState,
    onModelLoadFailure: (String) -> Unit,
    onLoadStateChanged: (Boolean, String?, Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader) {
        runCatching {
            environmentLoader.createHDREnvironment("environments/sky_2k.hdr")
        }.getOrNull() ?: createEnvironment(environmentLoader)
    }
    val cameraNode = rememberCameraNode(engine) {
        position = Position(z = 3.0f)
        lookAt(Position(0f, 0f, 0f))
    }
    val meshMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0.90f, 0.92f, 0.96f, 1f), metallic = 0f, roughness = 0.65f)
    }
    var modelInstance by remember { mutableStateOf<ModelInstance?>(null) }
    var meshBuffers by remember { mutableStateOf<MeshBuffers?>(null) }
    var cameraDistance by remember { mutableFloatStateOf(3f) }
    val modelLocation = (loadedAsset as? ImportedAsset.Model)?.fileLocation

    DisposableEffect(loadedAsset) {
        meshBuffers?.destroy(engine)
        meshBuffers = null

        if (loadedAsset is ImportedAsset.Mesh) {
            onLoadStateChanged(true, "Creating mesh buffers...", 0.95f)
            meshBuffers = createMeshBuffers(engine, loadedAsset.mesh)
            cameraDistance = computeFramedCameraDistance(loadedAsset.mesh.normalizedRadius)
            onLoadStateChanged(false, null, null)
        }

        if (modelLocation == null) {
            modelInstance = null
            onDispose { }
        } else {
            modelInstance = null
            onLoadStateChanged(true, "Loading model...", null)

            val job = modelLoader.loadModelInstanceAsync(modelLocation) { instance ->
                modelInstance = instance
                if (instance == null) {
                    onLoadStateChanged(false, null, null)
                    onModelLoadFailure(
                        "That file could not be loaded. SceneView loads GLB/GLTF directly; STL/OBJ are rendered as meshes."
                    )
                } else {
                    val modelBox = instance.model.boundingBox
                    val halfExtent = modelBox.halfExtent
                    val radius = maxOf(halfExtent[0], halfExtent[1], halfExtent[2])
                    cameraDistance = computeFramedCameraDistance(radius)
                    onLoadStateChanged(false, null, null)
                }
            }

            onDispose {
                job.cancel()
            }
        }
    }

    DisposableEffect(cameraDistance, transformState.tx, transformState.ty, transformState.tz) {
        val target = Position(transformState.tx, transformState.ty, transformState.tz)
        cameraNode.position = Position(target.x, target.y, target.z + cameraDistance)
        cameraNode.lookAt(target)
        onDispose { }
    }

    Box(modifier = modifier) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            environment = environment,
            cameraNode = cameraNode,
            cameraManipulator = rememberCameraManipulator(),
            mainLightNode = rememberMainLightNode(engine) {
                intensity = 100_000f
            }
        ) {
            when (val asset = loadedAsset) {
                is ImportedAsset.Model -> {
                    modelInstance?.let {
                        val modelBox = it.model.boundingBox
                        Node(
                            position = Position(
                                transformState.tx - modelBox.center[0],
                                transformState.ty - modelBox.center[1],
                                transformState.tz - modelBox.center[2]
                            ),
                            rotation = Rotation(transformState.rx, transformState.ry, transformState.rz),
                            scale = Scale(transformState.scale)
                        ) {
                            ModelNode(
                                modelInstance = it,
                                scaleToUnits = 1.0f,
                                autoAnimate = true
                            )
                        }
                    }
                }

                is ImportedAsset.Mesh -> {
                    val buffers = meshBuffers
                    if (buffers != null) {
                        val center = asset.mesh.center
                        val scale = asset.mesh.scale
                        Node(
                            position = Position(
                                -center.x * scale + transformState.tx,
                                -center.y * scale + transformState.ty,
                                -center.z * scale + transformState.tz
                            ),
                            rotation = Rotation(transformState.rx, transformState.ry, transformState.rz),
                            scale = Scale(scale * transformState.scale)
                        ) {
                            MeshNode(
                                primitiveType = com.google.android.filament.RenderableManager.PrimitiveType.TRIANGLES,
                                vertexBuffer = buffers.vertexBuffer,
                                indexBuffer = buffers.indexBuffer,
                                boundingBox = asset.mesh.boundingBox,
                                materialInstance = meshMaterial
                            )
                        }
                    }
                }

                null -> Unit
            }
        }
    }
}

private fun copySelectedModelToCache(context: Context, source: Uri): File {
    val displayName = resolveDisplayName(context, source) ?: "model.glb"
    val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]+"), "_")
    val destinationDirectory = File(context.cacheDir, "view3d-imports").apply { mkdirs() }
    val destinationFile = File(destinationDirectory, "${System.currentTimeMillis()}_$safeName")

    context.contentResolver.openInputStream(source)?.use { inputStream ->
        destinationFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    } ?: throw IllegalStateException("Unable to open the selected file.")

    return destinationFile
}

private fun resolveDisplayName(context: Context, source: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    context.contentResolver.query(source, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }

    return source.lastPathSegment?.substringAfterLast('/')
}

private fun computeFramedCameraDistance(radius: Float): Float {
    val safeRadius = radius.coerceAtLeast(0.4f)
    return (safeRadius * 2.8f).coerceAtLeast(2.2f)
}

@Preview(showBackground = true)
@Composable
fun ModelViewerScreenPreview() {
    View3dTheme {
        ModelViewerScreen()
    }
}