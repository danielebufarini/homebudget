package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AppWhitelistParserAndRemoteDataSourceTest {

    @Test
    fun parseWhitelistedAppsJson_readsPackageNameAndBankName() {
        val apps = parseWhitelistedAppsJson(
            """
            [
              { "packageName": "it.fineco.mobile", "bankName": "Fineco" },
              { "packageName": "com.unicredit", "bankName": "UniCredit" }
            ]
            """.trimIndent()
        )

        assertEquals(
            listOf(
                WhitelistedApp(packageName = "com.unicredit", bankName = "UniCredit"),
                WhitelistedApp(packageName = "it.fineco.mobile", bankName = "Fineco")
            ),
            apps
        )
    }

    @Test
    fun parseWhitelistedAppsJson_rejectsMalformedJson() {
        assertFailsWith<AppWhitelistSyncException.MalformedJson> {
            parseWhitelistedAppsJson("not json")
        }
    }

    @Test
    fun parseWhitelistedAppsJson_rejectsBlankPackageName() {
        assertFailsWith<AppWhitelistSyncException.InvalidEntry> {
            parseWhitelistedAppsJson("""[{ "packageName": " ", "bankName": "Fineco" }]""")
        }
    }

    @Test
    fun fetchWhitelist_acceptsSuccessfulJsonResponse() = runTest {
        val dataSource = KtorAppWhitelistRemoteDataSource(
            transport = AppWhitelistHttpTransport { _ ->
                AppWhitelistHttpResponse(
                    statusCode = 200,
                    contentType = "application/json; charset=utf-8",
                    body = """[{ "packageName": "it.fineco.mobile", "bankName": "Fineco" }]"""
                )
            }
        )

        assertEquals(
            listOf(WhitelistedApp(packageName = "it.fineco.mobile", bankName = "Fineco")),
            dataSource.fetchWhitelist()
        )
    }

    @Test
    fun fetchWhitelist_acceptsRawJsonResponseWithPlainTextContentType() = runTest {
        val dataSource = KtorAppWhitelistRemoteDataSource(
            transport = AppWhitelistHttpTransport { _ ->
                AppWhitelistHttpResponse(
                    statusCode = 200,
                    contentType = "text/plain; charset=utf-8",
                    body = """[{ "packageName": "it.fineco.mobile", "bankName": "Fineco" }]"""
                )
            }
        )

        assertEquals(
            listOf(WhitelistedApp(packageName = "it.fineco.mobile", bankName = "Fineco")),
            dataSource.fetchWhitelist()
        )
    }

    @Test
    fun fetchWhitelist_rejectsFailedHttpResponse() = runTest {
        val dataSource = KtorAppWhitelistRemoteDataSource(
            transport = AppWhitelistHttpTransport { _ ->
                AppWhitelistHttpResponse(
                    statusCode = 503,
                    contentType = "application/json",
                    body = "[]"
                )
            }
        )

        val error = assertFailsWith<AppWhitelistSyncException.HttpStatus> {
            dataSource.fetchWhitelist()
        }
        assertEquals(503, error.statusCode)
        assertTrue(error.isTransient)
    }

    @Test
    fun fetchWhitelist_rejectsNonJsonResponse() = runTest {
        val dataSource = KtorAppWhitelistRemoteDataSource(
            transport = AppWhitelistHttpTransport { _ ->
                AppWhitelistHttpResponse(
                    statusCode = 200,
                    contentType = "text/html",
                    body = "<html></html>"
                )
            }
        )

        assertFailsWith<AppWhitelistSyncException.NonJsonContent> {
            dataSource.fetchWhitelist()
        }
    }

    @Test
    fun fetchWhitelist_rejectsMalformedJsonResponse() = runTest {
        val dataSource = KtorAppWhitelistRemoteDataSource(
            transport = AppWhitelistHttpTransport { _ ->
                AppWhitelistHttpResponse(
                    statusCode = 200,
                    contentType = "application/json",
                    body = "not json"
                )
            }
        )

        assertFailsWith<AppWhitelistSyncException.MalformedJson> {
            dataSource.fetchWhitelist()
        }
    }
}
