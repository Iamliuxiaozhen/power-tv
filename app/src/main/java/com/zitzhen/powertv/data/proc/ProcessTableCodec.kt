package com.zitzhen.powertv.data.proc

/** One process record exchanged between the Shizuku service and the app. */
data class ProcRecord(
    val pid: Int,
    val uid: Int?,
    val state: String,
    val residentPages: Long,
    val cpuTicks: Long,
    val startTimeTicks: Long,
    val name: String,
)

/**
 * Encodes a process table as one tab-separated line per process.
 *
 * The format is intentionally plain text: the Shizuku user service runs in a
 * different process and can only hand strings back over Binder. Decoding is
 * tolerant so a single malformed line never discards the whole snapshot.
 */
object ProcessTableCodec {

    private const val FIELD_COUNT = 7
    private const val UID_UNKNOWN = -1
    private const val MAX_NAME_LENGTH = 80

    fun encode(records: List<ProcRecord>): String = buildString {
        records.forEach { record ->
            append(record.pid).append('\t')
            append(record.uid ?: UID_UNKNOWN).append('\t')
            append(record.state).append('\t')
            append(record.residentPages).append('\t')
            append(record.cpuTicks).append('\t')
            append(record.startTimeTicks).append('\t')
            append(sanitize(record.name)).append('\n')
        }
    }

    fun decode(text: String): List<ProcRecord> = text.lineSequence()
        .mapNotNull(::decodeLine)
        .toList()

    private fun decodeLine(line: String): ProcRecord? {
        val fields = line.split('\t')
        if (fields.size < FIELD_COUNT) return null

        val pid = fields[0].toIntOrNull() ?: return null
        val uid = fields[1].toIntOrNull()?.takeIf { it >= 0 }
        val residentPages = fields[3].toLongOrNull() ?: return null
        val cpuTicks = fields[4].toLongOrNull() ?: return null
        val startTimeTicks = fields[5].toLongOrNull() ?: return null
        val name = fields[6].trim()
        if (name.isEmpty()) return null

        return ProcRecord(
            pid = pid,
            uid = uid,
            state = fields[2],
            residentPages = residentPages,
            cpuTicks = cpuTicks,
            startTimeTicks = startTimeTicks,
            name = name,
        )
    }

    private fun sanitize(name: String): String = name
        .replace('\t', ' ')
        .replace('\n', ' ')
        .replace('\r', ' ')
        .take(MAX_NAME_LENGTH)
}
