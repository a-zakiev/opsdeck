package com.zakiev.spatialdashboard.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Builds a tiny binary glTF (.glb) with a single unit cube: x and z from
// -0.5 to 0.5, y from 0 to 1, so a non-uniform entity scale stretches it
// upwards. One model gets instanced for every bar and the base plate.
object BarsGlb {

    fun unitCube(r: Float, g: Float, b: Float, emissive: Float): ByteArray {
        val positions = ArrayList<Float>(24 * 3)
        val normals = ArrayList<Float>(24 * 3)
        val indices = ArrayList<Int>(36)
        addBox(positions, normals, indices, -0.5f, 0.5f, 0f, 1f, -0.5f, 0.5f)

        val posBytes = floatBytes(positions)
        val normBytes = floatBytes(normals)
        val idxBytes = intBytes(indices)
        val bin = posBytes + normBytes + idxBytes

        val json = """
            {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
            "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"indices":2,"material":0}]}],
            "materials":[{"doubleSided":true,"emissiveFactor":[${r * emissive},${g * emissive},${b * emissive}],"pbrMetallicRoughness":{"baseColorFactor":[$r,$g,$b,1.0],"metallicFactor":0.0,"roughnessFactor":0.55}}],
            "buffers":[{"byteLength":${bin.size}}],
            "bufferViews":[
            {"buffer":0,"byteOffset":0,"byteLength":${posBytes.size},"target":34962},
            {"buffer":0,"byteOffset":${posBytes.size},"byteLength":${normBytes.size},"target":34962},
            {"buffer":0,"byteOffset":${posBytes.size + normBytes.size},"byteLength":${idxBytes.size},"target":34963}],
            "accessors":[
            {"bufferView":0,"componentType":5126,"count":${positions.size / 3},"type":"VEC3","min":[-0.5,0.0,-0.5],"max":[0.5,1.0,0.5]},
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
}
