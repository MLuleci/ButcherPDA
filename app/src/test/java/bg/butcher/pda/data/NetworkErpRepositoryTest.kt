package bg.butcher.pda.data

import bg.butcher.pda.fake.FakeDataSource
import bg.butcher.pda.fake.FakeErpService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkErpRepositoryTest {
    private val repository = NetworkErpRepository(FakeErpService())

    @Test
    fun searchBom_returnsServiceData() = runTest {
        val result = repository.searchBom("item")
        assertEquals(FakeDataSource.bomSearchResult.data, result.getOrThrow())
    }
}
