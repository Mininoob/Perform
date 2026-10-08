package com.preform.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * When, and on which device, an organizing fact last changed: a book's folder, its
 * details, a folder existing. The newest stamp wins everywhere; a tie goes to the larger
 * device id, so every device settles on the same answer. A change applied from elsewhere
 * keeps its original stamp, so it never echoes back as new.
 */
@Serializable
data class Stamp(val at: Long, val by: String = "") : Comparable<Stamp> {
    override fun compareTo(other: Stamp): Int = compareValuesBy(this, other, Stamp::at, Stamp::by)

    companion object {
        /** Before anything was stamped: anything real outranks it. */
        val NEVER = Stamp(0, "")
    }
}

/** A folder as the library knows it, so empty folders and removed ones travel too. */
@Serializable
data class FolderStamp(val path: String, val removed: Boolean = false, val stamp: Stamp)

/** One organizing change made on a phone, sent with POST /organize. */
@Serializable
sealed class OrganizeChange {
    abstract val at: Long

    /** A book moved to [folder] ("" for the top of the library). */
    @Serializable
    @SerialName("bookFolder")
    data class BookFolder(val contentId: String, val folder: String, override val at: Long) : OrganizeChange()

    /** A folder made, or removed (its books move up a level, as on the computer). */
    @Serializable
    @SerialName("folder")
    data class Folder(val path: String, val removed: Boolean = false, override val at: Long) : OrganizeChange()

    /** A folder renamed in place; [to] is its whole new path, in the same parent. */
    @Serializable
    @SerialName("renameFolder")
    data class FolderRename(val from: String, val to: String, override val at: Long) : OrganizeChange()
}

@Serializable
data class OrganizeRequest(val changes: List<OrganizeChange>)

/** What came of a phone's changes: how many took, and which lost to something newer. */
@Serializable
data class OrganizeResult(val applied: Int, val lost: List<OrganizeChange> = emptyList())
