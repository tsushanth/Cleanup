package com.kreativekoala.cleanup.ui.archive

import com.kreativekoala.cleanup.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveErrorMapperTest {
    @Test
    fun parsesTheStableCodeFromALambdaBody() {
        assertEquals("ITEM_NOT_FOUND", ArchiveErrorMapper.parseCode("""{"error":"Item not found","code":"ITEM_NOT_FOUND"}"""))
        assertEquals("QUOTA_EXCEEDED", ArchiveErrorMapper.parseCode("""{"error":"Storage quota exceeded","code": "QUOTA_EXCEEDED","usedBytes":5}"""))
        assertNull(ArchiveErrorMapper.parseCode("""{"error":"Item not found"}"""))
        assertNull(ArchiveErrorMapper.parseCode(null))
    }

    @Test
    fun codeWinsOverStatus() {
        assertEquals(R.string.archive_error_user_not_found, ArchiveErrorMapper.resFor(404, """{"error":"User not found","code":"USER_NOT_FOUND"}"""))
        assertEquals(R.string.archive_error_quota_exceeded, ArchiveErrorMapper.resFor(413, """{"code":"QUOTA_EXCEEDED"}"""))
        assertEquals(R.string.archive_error_server, ArchiveErrorMapper.resFor(500, """{"code":"INTERNAL_ERROR"}"""))
    }

    @Test
    fun unknownCodeFallsBackToStatus() {
        assertEquals(R.string.archive_error_invalid_request, ArchiveErrorMapper.resFor(400, """{"code":"SOMETHING_NEW"}"""))
        assertEquals(R.string.archive_error_not_authenticated, ArchiveErrorMapper.resFor(403, null))
        assertEquals(R.string.archive_error_item_not_found, ArchiveErrorMapper.resFor(404, "Not Found"))
        assertEquals(R.string.archive_error_server, ArchiveErrorMapper.resFor(503, ""))
        assertEquals(R.string.archive_error_generic, ArchiveErrorMapper.resFor(418, null))
    }

    @Test
    fun everyLambdaCodeIsMapped() {
        val expected = setOf(
            "MISSING_IDENTITY", "MISSING_ITEM_ID", "MISSING_FIELDS", "ITEM_NOT_FOUND",
            "USER_NOT_FOUND", "QUOTA_EXCEEDED", "INTERNAL_ERROR"
        )
        assertEquals(expected, ArchiveErrorMapper.CODES.keys)
    }
}
