package com.zakiev.spatialdashboard.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Builds a tiny binary glTF (.glb) with one box per value, so the bar chart
// is real 3D geometry. Sizes are in meters, bars sit on y = 0.
object BarsGlb {

    fun build(values: List<Double>): ByteArray {
        val n = values.size
        val maxValue = values.maxOrNull()?.takeIf { it > 1e-9 } ?: 1.0

        val positions = ArrayList<Float>(n * 24 * 3)
        val normals = ArrayList<Float>(n * 24 * 3)
        val indices = ArrayList<Int>(n * 36)

        val totalWidth = n * BAR_W + (n - 1) * GAP
        values.forEachIndexed { i, value ->
            val h = ((value / maxValue).toFloat().coerceAtLeast(0.06f)) * MAX_H
            val x0 = -totalWidth / 2 + i * (BAR_W + GAP)
            addBox(positions, normals, indices, x0, x0 + BAR_W, 0f, h, -DEPTH / 2, DEPTH / 2)
        }

        val posBytes = floatBytes(positions)
        val normBytes = floatBytes(normals)
        val idxBytes = intBytes(indices)
        val bin = posBytes + normBytes + idxBytes

        val minY = 0f
        val maxY = MAX_H
        val json = """
            {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
            "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"indices":2,"material":0}]}],
            "materials":[{"doubleSided":true,"emissiveFactor":[0.13,0.42,0.38],"pbrMetallicRoughness":{"baseColorFactor":[0.37,0.92,0.83,1.0],"metallicFactor":0.0,"roughnessFactor":0.55}}],
            "buffers":[{"byteLength":${bin.size}}],
            "bufferViews":[
            {"buffer":0,"byteOffset":0,"byteLength":${posBytes.size},"target":34962},
            {"buffer":0,"byteOffset":${posBytes.size},"byteLength":${normBytes.size},"target":34962},
            {"buffer":0,"byteOffset":${posBytes.size + normBytes.size},"byteLength":${idxBytes.size},"target":34963}],
            "accessors":[
            {"bufferView":0,"componentType":5126,"count":${positions.size / 3},"type":"VEC3","min":[${-totalWidth / 2},$minY,${-DEPTH / 2}],"max":[${totalWidth / 2},$maxY,${DEPTH / 2}]},
            {"bufferView":1,"componentType":5126,"count":${normals.size / 3},"type":"VEC3"},
            {"bufferView":2,"componentType":5125,"count":${indices.size},"type":"SCALAR"}]}
        """.trimIndent().replace("\n", "")

        return glbContainer(json.toByteArray(Charsets.UTF_8), bin)
    }

    private fun addBox(
        positions: MutableList<Float>,
        normals: MutableList<Float>,
        indices: MutableList<Int>,
        x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float,
    ) {
        // 6 faces, 4 vertices each with the face normal
        val faces = arrayOf(
            arrayOf(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0f, 0f, 1f),
            arrayOf(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0f, 0f, -1f),
            arrayOf(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1f, 0f, 0f),
            arrayOf(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1f, 0f, 0f),
            arrayOf(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0f, 1f, 0f),
            arrayOf(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0f, -1f, 0f),
        )
        faces.forEach { f ->
            val base = positions.size / 3
            for (v in 0 until 4) {
                positions.add(f[v * 3]); positions.add(f[v * 3 + 1]); positions.add(f[v * 3 + 2])
                normals.add(f[12]); normals.add(f[13]); normals.add(f[14])
            }
            indices.addAll(listOf(base, base + 1, base + 2, base, base + 2, base + 3))
        }
    }

    private fun floatBytes(values: List<Float>): ByteArray {
        val buf = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buf.putFloat(it) }
        return buf.array()
    }

    private fun intBytes(values: List<Int>): ByteArray {
        val buf = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buf.putInt(it) }
        return buf.array()
    }

    private fun glbContainer(jsonRaw: ByteArray, bin: ByteArray): ByteArray {
        val json = jsonRaw + ByteArray((4 - jsonRaw.size % 4) % 4) { ' '.code.toByte() }
        val binPadded = bin + ByteArray((4 - bin.size % 4) % 4)
        val total = 12 + 8 + json.size + 8 + binPadded.size
        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x46546C67) // "glTF"
        buf.putInt(2)
        buf.putInt(total)
        buf.putInt(json.size)
        buf.putInt(0x4E4F534A) // "JSON"
        buf.put(json)
        buf.putInt(binPadded.size)
        buf.putInt(0x004E4942) // "BIN"
        buf.put(binPadded)
        return buf.array()
    }

    private const val BAR_W = 0.05f
    private const val GAP = 0.018f
    private const val DEPTH = 0.05f
    private const val MAX_H = 0.32f
}
