package bg.butcher.pda.network

import kotlinx.serialization.json.Json

// The server may add keys (new fields, `_server_messages`, whole documents)
val erpJson = Json { ignoreUnknownKeys = true }
