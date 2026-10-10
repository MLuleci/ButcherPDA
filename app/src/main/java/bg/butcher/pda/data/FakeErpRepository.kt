package bg.butcher.pda.data

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomDetailsItem
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Clock

class FakeErpRepository(
    private val latency: Duration = Duration.ZERO,
    private val failureRate: Float = 0f,
): ErpRepository {
    companion object {
        val alphabet = CharRange('a', 'z')
        val vowels = listOf('a', 'e', 'i', 'o', 'u')
        val consonants = alphabet.filter { !vowels.contains(it) }
    }

    private fun shouldFail(): Boolean {
        if (failureRate == 0f) return false
        val roll = IntRange(0, 100).random().toFloat()
        return roll < (failureRate * 100f)
    }

    private fun <T> withFailure(generator: () -> T): Result<T> {
        return if (shouldFail()) {
            Result.failure(Throwable("Unexpected error"))
        } else {
            Result.success(generator())
        }
    }

    private fun syllable(): String {
        val cv = String(charArrayOf(consonants.random(), vowels.random()))
        val cvc = String(charArrayOf(consonants.random(), vowels.random(), consonants.random()))
        return listOf(cv, cvc, vowels.random().toString()).random()
    }

    private fun fakeWord(): String {
        // https://stackoverflow.com/a/1891422
        return buildString {
            repeat (Random.nextInt(2, 3)) {
                append(syllable())
            }
        }
    }

    private fun fakeId(): String {
        return Random.nextInt(1, 999).toString().padStart(3, '0')
    }

    private fun fakeBomId(): String {
        return "BOM.${fakeId()}"
    }

    private fun fakeItemId(): String {
        return "SKU.${fakeId()}"
    }

    override suspend fun searchBom(query: String): Result<List<BomSearchResultItem>> {
        delay(latency)
        return withFailure {
            buildList<BomSearchResultItem> {
                repeat (Random.nextInt(10, 20)) {
                    val isDefault = if (Random.nextBoolean()) { 1 } else { 0 }
                    add(
                        BomSearchResultItem(
                            name = fakeBomId(),
                            item = fakeItemId(),
                            itemName = fakeWord(),
                            uom = "Kgs",
                            isDefault = isDefault,
                        )
                    )
                }
            }
        }
    }

    override suspend fun getBatch(batchNumber: String): Result<BatchDetails> {
        delay(latency)
        return withFailure {
            BatchDetails(
                name = batchNumber,
                item = fakeItemId(),
                itemName = fakeWord()
            )
        }
    }

    override suspend fun getBomDetails(
        bom: String,
        quantity: Float,
        explode: Boolean
    ): Result<BomDetails> {
        delay(latency)
        return withFailure {
            BomDetails(
                name = fakeBomId(),
                quantity = quantity,
                itemCode = fakeItemId(),
                itemName = fakeWord(),
                uom = "Kgs",
                stockUom = "Kgs",
                shelfLife = Random.nextInt(1, 7),
                items = buildList {
                    repeat(Random.nextInt(1, 5)) {
                        add(
                            BomDetailsItem(
                                quantity = (1f - Random.nextFloat()) * 10f,
                                itemCode = fakeItemId(),
                                itemName = fakeWord(),
                                uom = "Kgs",
                                stockUom = "Kgs",
                                conversionFactor = 1f,
                            )
                        )
                    }
                }
            )
        }
    }

    override suspend fun createManufactureEntry(
        bom: String,
        quantity: Float,
        items: Map<String, Map<String, Float>>,
        expiryDate: LocalDate?,
        explode: Boolean,
        isFinished: Boolean
    ): Result<ManufactureEntry> {
        delay(latency)
        return withFailure {
            ManufactureEntry(
                name = fakeItemId(),
                batchNumber = "BATCH.${fakeId()}",
                itemName = fakeWord(),
                productionDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date,
                expiryDate = expiryDate,
                isFinished = isFinished
            )
        }
    }

}