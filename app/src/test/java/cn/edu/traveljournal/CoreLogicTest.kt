package cn.edu.traveljournal

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CoreLogicTest {
    @Test fun endpointAcceptsBaseAndFullPath() {
        assertEquals("https://example.org/v1/chat/completions", normalizeEndpoint("https://example.org/v1/"))
        assertEquals("https://example.org/v1/chat/completions", normalizeEndpoint("https://example.org/v1/chat/completions/"))
        assertEquals("https://example.org/v1/chat/completions", normalizeEndpoint("https://example.org"))
        assertEquals("https://example.org/api/chat/completions", normalizeEndpoint("https://example.org/api"))
    }

    @Test fun endpointRejectsCredentialAndInsecureUrls() {
        listOf("http://example.org", "https://user:secret@example.org/v1", "https://example.org/v1?key=secret", "https://example.org/#token", "file:///tmp/a").forEach {
            assertThrows(IllegalArgumentException::class.java) { normalizeEndpoint(it) }
        }
    }

    @Test fun fencedJsonFiltersUnknownIdsAndDuplicates() {
        val parsed = AiParsers.search("```json\n{\"items\":[{\"id\":\"a\",\"reason\":\"海边\"},{\"id\":\"invented\"},{\"id\":\"a\"}]}\n```", setOf("a"))
        assertEquals(listOf(SearchHit("a", "海边")), parsed)
    }

    @Test fun malformedOrWrongTypeResultsAreErrors() {
        assertThrows(AiException::class.java) { AiParsers.search("{unfinished", setOf("a")) }
        assertThrows(AiException::class.java) { AiParsers.search("{\"items\":\"a\"}", setOf("a")) }
        assertThrows(AiException::class.java) { AiParsers.photo("{\"title\":123}") }
        assertEquals(emptyList<SearchHit>(), AiParsers.search("{\"items\":[]}", setOf("a")))
    }

    @Test fun recommendationsMustBeKnownAndNotExcluded() {
        val parsed = AiParsers.recommend("{\"understood\":\"两天看海，预算待确认\",\"items\":[{\"id\":\"a\",\"reason\":\"看海\"},{\"id\":\"b\"},{\"id\":\"unknown\"}]}", setOf("a", "b"), setOf("b"))
        assertEquals(listOf("a"), parsed.items.map { it.id })
        assertTrue(parsed.understood.contains("两天"))
    }

    @Test fun chineseKeywordSearchHasAnIndependentLocalBaseline() {
        val journals = listOf(
            Journal(id = "sea", title = "青岛之旅", body = "在海边看日落", tags = listOf("夕阳")),
            Journal(id = "hill", title = "爬山", body = "山顶的清晨"),
        )
        assertEquals("sea", localSearch("海边日落", journals).first().id)
        assertEquals("sea", localSearch("青岛", journals).single().id)
        assertTrue(localSearch("火星", journals).isEmpty())
        assertTrue(localSearch(" ", journals).isEmpty())
    }

    @Test fun zipPathsMustStayInsideTheArchiveNamespace() {
        listOf("../manifest.json", "/manifest.json", "photos/../../x", "photos\\evil.jpg", "C:/x", "photos/x/evil.jpg", "photos/.hidden.jpg").forEach {
            assertThrows(IllegalArgumentException::class.java) { validateBackupPath(it) }
        }
        validateBackupPath("manifest.json")
        validateBackupPath("photos/abc-123.jpg")
    }

    @Test fun boundedFileCopyStopsOversizedInputBeforeWritingTheExcess() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            copyLimited(ByteArrayInputStream(ByteArray(20)), output, 10)
        }
        assertTrue(output.size() <= 10)
        val exact = ByteArrayOutputStream()
        assertEquals(10L, copyLimited(ByteArrayInputStream(ByteArray(10)), exact, 10))
        assertEquals(10, exact.size())
    }

    @Test fun importedDestinationSourcesCannotBeFileOrScriptLinks() {
        listOf("file:///private/data", "javascript:alert(1)", "https://user:password@example.org").forEach { source ->
            assertThrows(IllegalArgumentException::class.java) { validateDestination(Destination(id = "a", name = "目的地", sourceUrl = source)) }
        }
        validateDestination(Destination(id = "a", name = "目的地", sourceUrl = "https://example.org/source"))
    }
}
