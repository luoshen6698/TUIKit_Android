package io.trtc.tuikit.chat.demo.xingdun.network

/** Loads the complete tenant-authoritative member list from the paged team endpoint. */
internal object XingDunGroupMemberPager {
    private const val PAGE_SIZE = 200
    private const val MAX_PAGE_COUNT = 100

    suspend fun loadAll(
        client: XingDunApiClient,
        session: XingDunStoredSession,
        groupID: String,
        invalidPaginationMessage: String,
    ): List<XingDunGroupMember> {
        val membersByID = linkedMapOf<String, XingDunGroupMember>()
        var pageNumber = 1
        while (pageNumber <= MAX_PAGE_COUNT) {
            val page = client.get<XingDunGroupMemberPage>(
                session,
                "team/members",
                mapOf(
                    "team_id" to groupID,
                    "page" to pageNumber.toString(),
                    "pageSize" to PAGE_SIZE.toString(),
                ),
                XingDunGroupMemberPage::class.java,
            )
            val previousSize = membersByID.size
            page.list.forEach { member ->
                member.userId.takeIf(String::isNotBlank)?.let { membersByID[it] = member }
            }
            val reachedReportedTotal = page.total > 0 && membersByID.size >= page.total
            val reachedLastPage = page.list.size < PAGE_SIZE
            if (page.list.isEmpty() || reachedReportedTotal || reachedLastPage) {
                return membersByID.values.toList()
            }
            if (membersByID.size == previousSize) error(invalidPaginationMessage)
            pageNumber += 1
        }
        error(invalidPaginationMessage)
    }
}
