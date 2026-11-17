package com.jackwallner.pelojack.workout

import java.io.File
import java.io.InputStream

/** Plans become visible only after a same-directory atomic rename. */
class PlanImporter internal constructor(
    private val directory: File,
    private val publish: (File, File) -> Boolean = { stage, target -> stage.renameTo(target) },
) {
    data class Preview internal constructor(val program: Program, val workouts: List<Workout>)
    fun preview(input: InputStream, zip: Boolean, workoutIds: Set<String>, programIds: Set<String>): Preview {
        val entries = if (zip) readZip(input) else mapOf("program.json" to readLimited(input, MAX_ENTRY))
        val program = parseProgram(checkNotNull(entries["program.json"]) { "Missing program.json" }.toString(Charsets.UTF_8))
        val workouts = entries.filterKeys { it != "program.json" }.map { (name, bytes) ->
            when {
                name.endsWith(".json") -> parseWorkout(bytes.toString(Charsets.UTF_8))
                name.endsWith(".zwo") -> parseZwo(bytes, "zwo-" + name.substringAfterLast('/').removeSuffix(".zwo"))
                else -> error("Unsupported workout file")
            }
        }
        validate(program, workouts, workoutIds, programIds)
        return Preview(program, workouts)
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "Import exceeds size limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    /** Validate central-directory types too: ZipInputStream alone hides Unix symlinks. */
    private fun readZip(input: InputStream): Map<String, ByteArray> {
        val bytes = readLimited(input, MAX_ARCHIVE)
        fun u16(at: Int): Int {
            require(at >= 0 && at + 2 <= bytes.size) { "Truncated ZIP" }
            return (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)
        }
        fun u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)
        val end = (bytes.size - 22 downTo maxOf(0, bytes.size - 65557)).firstOrNull {
            u32(it) == 0x06054b50L && it + 22 + u16(it + 20) == bytes.size
        } ?: error("Missing ZIP end record")
        require(u16(end + 4) == 0 && u16(end + 6) == 0 && u16(end + 8) == u16(end + 10)) { "Multi-disk ZIP unsupported" }
        val count = u16(end + 10)
        require(count in 1..128) { "ZIP must contain 1–128 entries" }
        val centralStart = u32(end + 16)
        require(centralStart + u32(end + 12) == end.toLong()) { "Invalid ZIP directory or ZIP64" }
        require(centralStart <= end) { "Invalid ZIP directory" }
        var cursor = centralStart.toInt()
        var total = 0L
        val metadata = linkedMapOf<String, Long>()
        repeat(count) {
            require(u32(cursor) == 0x02014b50L) { "Invalid ZIP central record" }
            val flags = u16(cursor + 8)
            require(flags and 1 == 0 && u16(cursor + 10) in listOf(0, 8)) { "Encrypted or unsupported ZIP" }
            val size = u32(cursor + 24)
            val compressed = u32(cursor + 20)
            require(size <= MAX_ENTRY && compressed <= MAX_ARCHIVE && size <= maxOf(1L, compressed) * 200) { "ZIP entry too large or compression ratio over 200" }
            total += size
            require(total <= MAX_TOTAL) { "ZIP uncompressed total exceeds limit" }
            val nameLength = u16(cursor + 28)
            val next = cursor + 46 + nameLength + u16(cursor + 30) + u16(cursor + 32)
            require(next <= end && u32(cursor + 42) < centralStart && u16(cursor + 34) == 0) { "Invalid ZIP offsets" }
            val name = bytes.copyOfRange(cursor + 46, cursor + 46 + nameLength).toString(Charsets.UTF_8)
            require(name == "program.json" || name == "workouts/" || Regex("workouts/[a-z0-9][a-z0-9_-]{0,74}\\.(json|zwo)").matches(name)) { "Unsafe or unsupported ZIP entry: $name" }
            val type = (u32(cursor + 38) shr 16).toInt() and 0xf000
            require(type == 0 || type == 0x8000 || (type == 0x4000 && name == "workouts/")) { "ZIP links and special files are forbidden" }
            require(!metadata.containsKey(name)) { "Duplicate ZIP entry" }
            metadata[name] = size
            cursor = next
        }
        require(cursor == end) { "Invalid ZIP central directory length" }
        val result = linkedMapOf<String, ByteArray>()
        val seen = mutableSetOf<String>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(seen.add(entry.name) && entry.name in metadata) { "Duplicate or mismatched ZIP entry" }
                val content = readLimited(zip, MAX_ENTRY)
                require(content.size.toLong() == metadata[entry.name]) { "ZIP size mismatch" }
                if (entry.name == "workouts/") require(content.isEmpty()) { "Invalid directory entry" }
                else result[entry.name] = content
            }
        }
        require(seen == metadata.keys) { "ZIP local and central records disagree" }
        return result
    }

    companion object {
        private val COMMIT_LOCK = Any()
        private const val MAX_ENTRY = 1024 * 1024
        private const val MAX_ARCHIVE = 4 * 1024 * 1024
        private const val MAX_TOTAL = 8 * 1024 * 1024
    }

    private fun jsonObject(text: String) = ImportJson.objectOf(text)

    private fun parseWorkout(text: String): Workout {
        val root = jsonObject(text)
        require(root.get("id") is String && root.get("name") is String) { "Workout id and name must be strings" }
        require(!root.has("video")) { "Bundled videos are unsupported in v1" }
        val segments = root.getJSONArray("segments")
        require(segments.length() in 1..4096) { "Expected 1–4096 workout segments" }
        for (i in 0 until segments.length()) {
            val segment = segments.getJSONObject(i)
            require(segment.get("name") is String) { "Segment name must be a string" }
            integer(segment.get("seconds"), 1..21600)
            if (segment.has("zone")) integer(segment.get("zone"), 1..7)
            if (segment.has("ftp")) number(segment.get("ftp"), 0.0..3.0)
            for ((key, range) in listOf("resistance" to (0..100), "cadence" to (0..250))) {
                if (!segment.has(key)) continue
                val raw = segment.get(key)
                if (raw is org.json.JSONArray) {
                    require(raw.length() == 2) { "Target range must contain two integers" }
                    val low = integer(raw.get(0), range)
                    require(integer(raw.get(1), range) >= low) { "Reversed target range" }
                } else integer(raw, range)
            }
        }
        return Workout.parse(text, custom = true).also(::validateWorkout)
    }

    private fun number(value: Any, range: ClosedFloatingPointRange<Double>): Double {
        require(value is Number && value.toDouble().isFinite() && value.toDouble() in range) { "Invalid numeric workout target" }
        return value.toDouble()
    }
    private fun integer(value: Any, range: IntRange): Int {
        val valueAsDouble = number(value, range.first.toDouble()..range.last.toDouble())
        require(valueAsDouble % 1.0 == 0.0) { "Expected integer" }
        return valueAsDouble.toInt()
    }
    private fun validateWorkout(workout: Workout) {
        require(workout.toJson().toString().toByteArray(Charsets.UTF_8).size <= MAX_ENTRY) { "Normalized workout exceeds storage limit" }
        require(safeId(workout.id) && workout.name.isNotBlank() && workout.name.length <= 200) { "Invalid workout id or name" }
        require(workout.segments.size in 1..4096 && workout.segments.sumOf { it.seconds.toLong() } in 1..21600) { "Workout must be at most six hours" }
        workout.segments.forEach {
            require(it.seconds > 0 && it.name.isNotBlank() && it.name.length <= 200) { "Invalid workout segment" }
            it.ftpShare?.let { share -> require(share.isFinite() && share in 0.0..3.0) { "Invalid FTP target" } }
        }
    }

    private fun parseZwo(bytes: ByteArray, id: String): Workout {
        val text = bytes.toString(Charsets.UTF_8)
        require(!text.contains("<!DOCTYPE", ignoreCase = true) && !text.contains("<!ENTITY", ignoreCase = true) && !text.contains('\u0000')) { "XML declarations/entities forbidden" }
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }
        // Parse UTF-8 text, not the original bytes: alternative encodings cannot bypass the declaration check.
        val root = factory.newDocumentBuilder().parse(text.byteInputStream()).documentElement
        require(root.tagName == "workout_file") { "Expected workout_file" }
        val workouts = root.getElementsByTagName("workout")
        require(workouts.length == 1) { "Expected exactly one workout" }
        val steps = workouts.item(0).childNodes
        var expanded = 0
        for (i in 0 until steps.length) {
            val step = steps.item(i) as? org.w3c.dom.Element ?: continue
            fun numeric(key: String, range: ClosedFloatingPointRange<Double>) = number(step.getAttribute(key).toDoubleOrNull() ?: error("Missing or invalid $key"), range)
            fun duration(key: String): Int = integer(numeric(key, 1.0..21600.0), 1..21600)
            when (step.tagName) {
                "SteadyState" -> { duration("Duration"); numeric("Power", 0.0..3.0); expanded++ }
                "Warmup", "Cooldown", "Ramp" -> { expanded += (duration("Duration") + 59) / 60; numeric("PowerLow", 0.0..3.0); numeric("PowerHigh", 0.0..3.0) }
                "FreeRide", "MaxEffort" -> { duration("Duration"); expanded++ }
                "IntervalsT" -> {
                    expanded += 2 * integer(numeric("Repeat", 1.0..100.0), 1..100)
                    duration("OnDuration"); duration("OffDuration")
                    numeric("OnPower", 0.0..3.0); numeric("OffPower", 0.0..3.0)
                }
                else -> error("Unsupported ZWO step: ${step.tagName}")
            }
            for (key in listOf("Cadence", "CadenceResting")) if (step.hasAttribute(key)) numeric(key, 0.0..245.0)
            require(expanded <= 4096) { "Too many ZWO segments" }
        }
        val converted = ZwoImporter.parse(text.byteInputStream(), id)
        // ZWO targets use a +/-5 rpm band; clip only the generated low edge at zero.
        // Keep the existing standalone ZWO converter unchanged, and validate the exact
        // normalized JSON that confirmation and reload will parse before showing Preview.
        val normalized = converted.copy(segments = converted.segments.map { segment ->
            segment.copy(cadence = segment.cadence?.let { maxOf(0, it.first)..it.last })
        })
        return parseWorkout(normalized.toJson().toString())
    }

    private fun parseProgram(text: String): Program {
        val root = jsonObject(text)
        require(root.get("formatVersion") == 1) { "Expected formatVersion 1" }
        require(root.get("id") is String && root.get("name") is String) { "id and name must be strings" }
        require(!root.has("description") || root.get("description") is String) { "description must be a string" }
        val weeks = root.getJSONArray("weeks")
        require(weeks.length() in 1..52) { "Expected 1–52 weeks" }
        for (w in 0 until weeks.length()) {
            val rides = weeks.getJSONArray(w)
            require(rides.length() in 1..14) { "Expected 1–14 rides per week" }
            for (r in 0 until rides.length()) require(rides.get(r) is String) { "Workout references must be strings" }
        }
        return Program.parse(text).also {
            require(safeId(it.id)) { "Unsafe plan id" }
            require(it.name.isNotBlank() && it.name.length <= 200 && it.description.length <= 10000) { "Invalid plan name or description" }
            require(it.workoutIds.all(::safeId)) { "Unsafe workout reference" }
            require(it.workoutIds.distinct().size == it.workoutIds.size) { "Duplicate workout reference" }
        }
    }

    private fun validate(program: Program, workouts: List<Workout>, workoutIds: Set<String>, programIds: Set<String>) {
        require(program.id !in programIds && !File(directory, program.id).exists() && !java.nio.file.Files.isSymbolicLink(File(directory, program.id).toPath()) && !File(directory, "${program.id}.json").exists()) { "Plan id already exists; nothing replaced" }
        require(workouts.map { it.id }.distinct().size == workouts.size) { "Duplicate workout id" }
        require(workouts.none { it.id in workoutIds }) { "Workout id already exists; nothing replaced" }
        require(workouts.all { it.id in program.workoutIds }) { "Bundle contains unreferenced workouts" }
        require(program.workoutIds.all { it in workoutIds || workouts.any { workout -> workout.id == it } }) { "Plan references missing workouts" }
    }

    private fun safeId(id: String) = Regex("[a-z0-9][a-z0-9_-]{0,79}").matches(id)

    fun commit(preview: Preview, workoutIds: Set<String>, programIds: Set<String>): Unit = synchronized(COMMIT_LOCK) {
        require(!java.nio.file.Files.isSymbolicLink(directory.toPath())) { "Symlink plan storage is forbidden" }
        parseProgram(toJson(preview.program))
        preview.workouts.forEach { parseWorkout(it.toJson().toString()) }
        validate(preview.program, preview.workouts, workoutIds + loadWorkouts().map { it.id }, programIds + loadPrograms(workoutIds).map { it.id })
        directory.mkdirs()
        val stage = File(directory, ".pending-${java.util.UUID.randomUUID()}")
        check(stage.mkdir())
        try {
            File(stage, "program.json").writeText(toJson(preview.program))
            val workoutDirectory = File(stage, "workouts")
            check(workoutDirectory.mkdir())
            preview.workouts.forEach { File(workoutDirectory, "${it.id}.json").writeText(it.toJson().toString()) }
            check(publish(stage, File(directory, preview.program.id))) { "Could not publish plan" }
        } finally { stage.deleteRecursively() }
    }
    private fun regular(file: File) = java.nio.file.Files.isRegularFile(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
    private fun children(): List<File> = if (java.nio.file.Files.isSymbolicLink(directory.toPath())) emptyList()
        else directory.listFiles().orEmpty().filter { !it.name.startsWith(".") && !java.nio.file.Files.isSymbolicLink(it.toPath()) }.sortedBy { it.name }
    private fun readText(file: File): String {
        require(regular(file)) { "Expected regular file" }
        return file.inputStream().use { readLimited(it, MAX_ENTRY) }.toString(Charsets.UTF_8)
    }
    fun loadPrograms(workoutIds: Set<String>): List<Program> {
        val candidates = children().mapNotNull { file -> runCatching {
            val program = parseProgram(readText(if (file.isDirectory) File(file, "program.json") else file))
            require(program.workoutIds.all { it in workoutIds })
            program
        }.getOrNull() }
        return candidates.groupBy { it.id }.values.filter { it.size == 1 }.map { it.single() }
    }
    fun loadWorkouts(): List<Workout> = children()
        .filter { it.isDirectory && regular(File(it, "program.json")) }
        .map { File(it, "workouts") }.filter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }
        .flatMap { it.listFiles().orEmpty().toList() }.filter { it.extension == "json" && regular(it) }
        .mapNotNull { runCatching { parseWorkout(readText(it)) }.getOrNull() }

    private fun toJson(program: Program) = org.json.JSONObject().apply {
        put("formatVersion", 1); put("id", program.id); put("name", program.name)
        put("description", program.description)
        put("weeks", org.json.JSONArray(program.weeks.map { org.json.JSONArray(it) }))
    }.toString(2)
}
