package bg.butcher.pda.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The parts of GitHub's `releases/latest` response that self-update reads. */
@Serializable
data class GithubRelease(
    @SerialName("tag_name")
    val tagName: String,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url")
    val downloadUrl: String,
    val size: Long,
)
