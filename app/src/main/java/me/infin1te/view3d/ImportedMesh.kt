package me.infin1te.view3d

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute.POSITION
import com.google.android.filament.VertexBuffer.VertexAttribute.TANGENTS
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.math.Position
import io.github.sceneview.math.normalToTangent
import io.github.sceneview.safeDestroyIndexBuffer
import io.github.sceneview.safeDestroyVertexBuffer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

sealed class ImportedAsset(open val displayName: String) {
    data class Model(
        override val displayName: String,
        val fileLocation: String,
    ) : ImportedAsset(displayName)

    data class Mesh(
        override val displayName: String,
        val mesh: ImportedMesh,
    ) : ImportedAsset(displayName)
}

data class ImportedMesh(
    val positions: FloatArray,
    val normals: FloatArray,
    val indices: IntArray,
    val center: Position,
    val scale: Float,
    val normalizedRadius: Float,
    val boundingBox: Box,
)

data class MeshBuffers(
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
) {
    fun destroy(engine: Engine) {
        engine.safeDestroyVertexBuffer(vertexBuffer)
        engine.safeDestroyIndexBuffer(indexBuffer)
    }
}

fun loadImportedAsset(file: File, displayName: String = file.name): ImportedAsset {
    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.US)

    return when (extension) {
        "glb", "gltf" -> ImportedAsset.Model(
            displayName = displayName,
            fileLocation = file.toURI().toString()
        )
        "stl" -> ImportedAsset.Mesh(displayName, parseStlMesh(file))
        "obj" -> ImportedAsset.Mesh(displayName, parseObjMesh(file))
        else -> throw IllegalArgumentException("Unsupported file type: .$extension")
    }
}

fun createMeshBuffers(engine: Engine, mesh: ImportedMesh): MeshBuffers {
    val vertexCount = mesh.positions.size / 3

    val vertexBuffer = VertexBuffer.Builder()
        .bufferCount(2)
        .attribute(POSITION, 0, AttributeType.FLOAT3)
        .attribute(TANGENTS, 1, AttributeType.FLOAT4)
        .normalized(TANGENTS)
        .vertexCount(vertexCount)
        .build(engine)

    val positionBytes = mesh.positions.toByteBuffer()
    val tangentBytes = mesh.normals.toTangents().toByteBuffer()
    vertexBuffer.setBufferAt(engine, 0, positionBytes, 0, positionBytes.capacity())
    vertexBuffer.setBufferAt(engine, 1, tangentBytes, 0, tangentBytes.capacity())

    val indexBuffer = IndexBuffer.Builder()
        .bufferType(IndexBuffer.Builder.IndexType.UINT)
        .indexCount(mesh.indices.size)
        .build(engine)

    val indexBytes = mesh.indices.toByteBuffer()
    indexBuffer.setBuffer(engine, indexBytes, 0, indexBytes.capacity())

    return MeshBuffers(vertexBuffer, indexBuffer)
}

private fun parseStlMesh(file: File): ImportedMesh {
    val bytes = file.readBytes()
    return if (looksLikeBinaryStl(bytes)) {
        parseBinaryStl(bytes)
    } else {
        parseAsciiStl(file.readText())
    }
}

private fun parseBinaryStl(bytes: ByteArray): ImportedMesh {
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    buffer.position(80)
    val triangleCount = buffer.int

    val positions = ArrayList<Float>(triangleCount * 9)
    val normals = ArrayList<Float>(triangleCount * 9)
    val indices = mutableListOf<Int>()

    repeat(triangleCount) {
        val normal = readFloat3(buffer)
        val a = readFloat3(buffer)
        val b = readFloat3(buffer)
        val c = readFloat3(buffer)
        buffer.short

        val faceNormal = if (isZeroVector(normal)) faceNormal(a, b, c) else normal
        val base = positions.size / 3
        appendVertex(positions, normals, a, faceNormal)
        appendVertex(positions, normals, b, faceNormal)
        appendVertex(positions, normals, c, faceNormal)
        indices.add(base)
        indices.add(base + 1)
        indices.add(base + 2)
    }

    return buildImportedMesh(positions, normals, indices.toIntArray())
}

private fun parseAsciiStl(text: String): ImportedMesh {
    val positions = ArrayList<Float>()
    val normals = ArrayList<Float>()
    val indices = mutableListOf<Int>()

    var currentNormal = Float3(0f, 1f, 0f)
    val faceVertices = ArrayList<Float3>(3)

    text.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("facet normal", ignoreCase = true) -> {
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 5) {
                    currentNormal = Float3(parts[2].toFloat(), parts[3].toFloat(), parts[4].toFloat())
                }
            }
            line.startsWith("vertex", ignoreCase = true) -> {
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 4) {
                    faceVertices.add(Float3(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat()))
                }
            }
            line.startsWith("endfacet", ignoreCase = true) -> {
                if (faceVertices.size >= 3) {
                    val a = faceVertices[0]
                    val b = faceVertices[1]
                    val c = faceVertices[2]
                    val faceNormal = if (isZeroVector(currentNormal)) faceNormal(a, b, c) else currentNormal
                    val base = positions.size / 3
                    appendVertex(positions, normals, a, faceNormal)
                    appendVertex(positions, normals, b, faceNormal)
                    appendVertex(positions, normals, c, faceNormal)
                    indices.add(base)
                    indices.add(base + 1)
                    indices.add(base + 2)
                }
                faceVertices.clear()
            }
        }
    }

    return buildImportedMesh(positions, normals, indices.toIntArray())
}

private fun parseObjMesh(file: File): ImportedMesh {
    val positionsSource = mutableListOf<Float3>()
    val normalsSource = mutableListOf<Float3>()

    val positions = ArrayList<Float>()
    val normals = ArrayList<Float>()
    val indices = mutableListOf<Int>()

    file.forEachLine { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) {
            return@forEachLine
        }

        val parts = line.split(Regex("\\s+"))
        when (parts[0]) {
            "v" -> if (parts.size >= 4) {
                positionsSource.add(Float3(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat()))
            }
            "vn" -> if (parts.size >= 4) {
                normalsSource.add(Float3(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat()))
            }
            "f" -> if (parts.size >= 4) {
                val face = parts.drop(1).map { parseObjVertexRef(it) }
                for (triangle in 1 until face.size - 1) {
                    val a = face[0]
                    val b = face[triangle]
                    val c = face[triangle + 1]
                    val pa = positionsSource.resolveObjIndex(a.positionIndex)
                    val pb = positionsSource.resolveObjIndex(b.positionIndex)
                    val pc = positionsSource.resolveObjIndex(c.positionIndex)
                    val computedNormal = faceNormal(pa, pb, pc)
                    val na = a.normalIndex?.let { normalsSource.resolveObjIndex(it) } ?: computedNormal
                    val nb = b.normalIndex?.let { normalsSource.resolveObjIndex(it) } ?: computedNormal
                    val nc = c.normalIndex?.let { normalsSource.resolveObjIndex(it) } ?: computedNormal

                    val base = positions.size / 3
                    appendVertex(positions, normals, pa, na)
                    appendVertex(positions, normals, pb, nb)
                    appendVertex(positions, normals, pc, nc)
                    indices.add(base)
                    indices.add(base + 1)
                    indices.add(base + 2)
                }
            }
        }
    }

    return buildImportedMesh(positions, normals, indices.toIntArray())
}

private fun buildImportedMesh(
    positions: List<Float>,
    normals: List<Float>,
    indices: IntArray,
): ImportedMesh {
    require(positions.isNotEmpty()) { "The selected file did not contain any mesh triangles." }

    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY

    for (index in positions.indices step 3) {
        val x = positions[index]
        val y = positions[index + 1]
        val z = positions[index + 2]
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (z < minZ) minZ = z
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
        if (z > maxZ) maxZ = z
    }

    val centerX = (minX + maxX) * 0.5f
    val centerY = (minY + maxY) * 0.5f
    val centerZ = (minZ + maxZ) * 0.5f
    val halfExtentX = (maxX - minX) * 0.5f
    val halfExtentY = (maxY - minY) * 0.5f
    val halfExtentZ = (maxZ - minZ) * 0.5f
    val maxExtent = maxOf(halfExtentX, halfExtentY, halfExtentZ)
    val scale = if (maxExtent > 0f) 1f / maxExtent else 1f
    val normalizedRadius = kotlin.math.sqrt(
        halfExtentX * halfExtentX +
            halfExtentY * halfExtentY +
            halfExtentZ * halfExtentZ
    ) * scale

    return ImportedMesh(
        positions = positions.toFloatArray(),
        normals = normals.toFloatArray(),
        indices = indices,
        center = Position(centerX, centerY, centerZ),
        scale = scale,
        normalizedRadius = normalizedRadius,
        boundingBox = Box(centerX, centerY, centerZ, halfExtentX, halfExtentY, halfExtentZ)
    )
}

private fun appendVertex(
    positions: MutableList<Float>,
    normals: MutableList<Float>,
    position: Float3,
    normal: Float3,
) {
    positions.add(position.x)
    positions.add(position.y)
    positions.add(position.z)

    normals.add(normal.x)
    normals.add(normal.y)
    normals.add(normal.z)
}

private fun MutableList<Float>.toFloatArray(): FloatArray {
    val array = FloatArray(size)
    for (index in indices) {
        array[index] = this[index]
    }
    return array
}

private fun IntArray.toByteBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(size * Int.SIZE_BYTES).order(ByteOrder.nativeOrder()).apply {
        asIntBuffer().put(this@toByteBuffer)
    }

private fun FloatArray.toByteBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).apply {
        asFloatBuffer().put(this@toByteBuffer)
    }

private fun FloatArray.toTangents(): FloatArray {
    require(size % 3 == 0)
    val tangents = FloatArray(size / 3 * 4)
    var tangentIndex = 0
    for (index in indices step 3) {
        val tangent = normalToTangent(Float3(this[index], this[index + 1], this[index + 2]))
        tangents[tangentIndex++] = tangent.x
        tangents[tangentIndex++] = tangent.y
        tangents[tangentIndex++] = tangent.z
        tangents[tangentIndex++] = tangent.w
    }
    return tangents
}

private data class ObjVertexRef(val positionIndex: Int, val normalIndex: Int?)

private fun parseObjVertexRef(token: String): ObjVertexRef {
    val parts = token.split('/')
    val positionIndex = parts[0].toInt()
    val normalIndex = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toInt()
    return ObjVertexRef(positionIndex, normalIndex)
}

private fun <T> List<T>.resolveObjIndex(index: Int): T =
    if (index > 0) this[index - 1] else this[size + index]

private fun readFloat3(buffer: ByteBuffer): Float3 =
    Float3(buffer.float, buffer.float, buffer.float)

private fun isZeroVector(vector: Float3): Boolean =
    vector.x == 0f && vector.y == 0f && vector.z == 0f

private fun faceNormal(a: Float3, b: Float3, c: Float3): Float3 {
    val ux = b.x - a.x
    val uy = b.y - a.y
    val uz = b.z - a.z
    val vx = c.x - a.x
    val vy = c.y - a.y
    val vz = c.z - a.z
    val nx = uy * vz - uz * vy
    val ny = uz * vx - ux * vz
    val nz = ux * vy - uy * vx
    val length = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
    return if (length > 0f) Float3(nx / length, ny / length, nz / length) else Float3(0f, 1f, 0f)
}

private fun looksLikeBinaryStl(bytes: ByteArray): Boolean {
    if (bytes.size < 84) return false
    val triangleCount = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
    return 84 + triangleCount * 50 == bytes.size
}
