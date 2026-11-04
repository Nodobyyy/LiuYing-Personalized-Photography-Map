package cn.photomap.core

import kotlin.math.floor

/** Input coordinates are projected screen pixels, never geographic degrees. */
data class PhotoPoint(val id: String, val x: Double, val y: Double, val photoCount: Int)
data class PhotoCluster(val members: List<PhotoPoint>, val coverId: String) {
    val key = members.joinToString("|") { it.id }
    val photoCount: Long = members.sumOf { it.photoCount.toLong() }
    val x = members.first().x
    val y = members.first().y
}

/** Stable, non-transitive radius grouping. A dense chain cannot swallow the whole map.
 * Anchors are stable IDs, independent of input order and camera translation.
 * Run on a worker; projection and rendering belong to the map adapter.
 */
object PhotoClustering {
    fun cluster(points: List<PhotoPoint>, radius: Double, sessionSeed: Int): List<PhotoCluster> {
        require(radius.isFinite() && radius > 0)
        require(points.map { it.id }.toSet().size == points.size) { "Duplicate location ID" }
        require(points.all { it.x.isFinite() && it.y.isFinite() && it.photoCount > 0 })
        val groups = mutableListOf<MutableList<PhotoPoint>>()
        val cells = HashMap<Pair<Long, Long>, MutableList<Int>>()
        points.sortedBy { it.id }.forEach { point ->
            val cx = floor(point.x / radius).toLong()
            val cy = floor(point.y / radius).toLong()
            var best: Int? = null
            var distance = radius * radius
            for (dx in -1L..1L) for (dy in -1L..1L) {
                cells[cx + dx to cy + dy]?.forEach { index ->
                    val anchor = groups[index].first()
                    val d = (point.x - anchor.x) * (point.x - anchor.x) +
                        (point.y - anchor.y) * (point.y - anchor.y)
                    if (d < distance || (d == distance && (best == null || index < best!!))) {
                        best = index; distance = d
                    }
                }
            }
            if (best != null) groups[best!!].add(point) else {
                cells.getOrPut(cx to cy) { mutableListOf() }.add(groups.size)
                groups.add(mutableListOf(point))
            }
        }
        return groups.map { members ->
            val hash = members.fold(sessionSeed) { acc, p -> 31 * acc + p.id.hashCode() }
            PhotoCluster(members, members[Math.floorMod(hash, members.size)].id)
        }
    }
}
