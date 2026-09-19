package ru.sdvirk.healthsync.watch

object WatchSyncCodec {

    fun toLine(sample: WatchSample): String = buildString {
        append("{\"type\":\"").append(escape(sample.type)).append("\",\"t\":")
        append(sample.timeEpochMs).append(",\"v\":").append(sample.value)
        sample.value2?.let { append(",\"v2\":").append(it) }
        sample.extra?.let { append(",\"x\":\"").append(escape(it)).append('"') }
        if (sample.source.isNotEmpty()) {
            append(",\"src\":\"").append(escape(sample.source)).append('"')
        }
        append('}')
    }

    fun fromLine(line: String): WatchSample? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        val type = stringField(trimmed, "type") ?: return null
        val t = longField(trimmed, "t") ?: return null
        val v = doubleField(trimmed, "v") ?: return null
        return WatchSample(
            type = type,
            timeEpochMs = t,
            value = v,
            value2 = doubleField(trimmed, "v2"),
            extra = stringField(trimmed, "x"),
            source = stringField(trimmed, "src") ?: WatchSample.SOURCE_SENSOR,
        )
    }

    fun encodeMessage(samples: List<WatchSample>): String = buildString {
        append("{\"v\":1,\"samples\":[")
        samples.forEachIndexed { i, sample ->
            if (i > 0) append(',')
            append(toLine(sample))
        }
        append("]}")
    }

    fun decodeMessage(json: String): List<WatchSample> {
        val start = json.indexOf('[')
        val end = json.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val body = json.substring(start + 1, end).trim()
        if (body.isEmpty()) return emptyList()
        val samples = mutableListOf<WatchSample>()
        var depth = 0
        var objStart = -1
        for (i in body.indices) {
            when (body[i]) {
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objStart >= 0) {
                        fromLine(body.substring(objStart, i + 1))?.let { samples += it }
                        objStart = -1
                    }
                }
            }
        }
        return samples
    }

    fun chunk(samples: List<WatchSample>, maxBytes: Int = 70_000): List<ByteArray> {
        if (samples.isEmpty()) return emptyList()
        val chunks = mutableListOf<ByteArray>()
        var batch = ArrayList<WatchSample>()
        for (sample in samples) {
            batch.add(sample)
            val bytes = encodeMessage(batch).toByteArray(Charsets.UTF_8)
            if (bytes.size > maxBytes && batch.size > 1) {
                batch.removeAt(batch.lastIndex)
                chunks += encodeMessage(batch).toByteArray(Charsets.UTF_8)
                batch = arrayListOf(sample)
            }
        }
        if (batch.isNotEmpty()) {
            chunks += encodeMessage(batch).toByteArray(Charsets.UTF_8)
        }
        return chunks
    }

    private fun stringField(json: String, key: String): String? {
        val needle = "\"$key\":\""
        val start = json.indexOf(needle)
        if (start < 0) return null
        val from = start + needle.length
        val out = StringBuilder()
        var i = from
        while (i < json.length) {
            val ch = json[i]
            if (ch == '\\' && i + 1 < json.length) {
                out.append(json[i + 1])
                i += 2
                continue
            }
            if (ch == '"') break
            out.append(ch)
            i++
        }
        return out.toString().ifEmpty { null }
    }

    private fun longField(json: String, key: String): Long? {
        val needle = "\"$key\":"
        val start = json.indexOf(needle)
        if (start < 0) return null
        val from = start + needle.length
        val num = json.substring(from).takeWhile { it.isDigit() || it == '-' }
        return num.toLongOrNull()
    }

    private fun doubleField(json: String, key: String): Double? {
        val needle = "\"$key\":"
        val start = json.indexOf(needle)
        if (start < 0) return null
        val from = start + needle.length
        val num = json.substring(from).takeWhile {
            it.isDigit() || it == '-' || it == '+' || it == '.' || it == 'e' || it == 'E'
        }
        return num.toDoubleOrNull()
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(ch)
            }
        }
    }
}
