package xyz.cssxsh.mirai.plugin

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import net.mamoe.mirai.utils.warning
import xyz.cssxsh.bilibili.data.*
import xyz.cssxsh.mirai.plugin.BilibiliHelperPlugin.bilibiliClient
import xyz.cssxsh.mirai.plugin.BilibiliHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.BilibiliChromeDriverConfig.timeoutMillis
import xyz.cssxsh.mirai.plugin.data.BilibiliChromeDriverConfig.chromePath
import xyz.cssxsh.mirai.plugin.data.BilibiliChromeDriverConfig.deviceName
import xyz.cssxsh.mirai.plugin.data.BilibiliChromeDriverConfig.driverUrl
import xyz.cssxsh.mirai.plugin.data.BilibiliHelperSettings.cachePath
import xyz.cssxsh.mirai.plugin.tools.BilibiliChromeDriverTool
import xyz.cssxsh.mirai.plugin.tools.getScreenShot
import java.io.File
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.seconds

internal val BILI_JSON = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
    allowStructuredMapKeys = true
}

private fun Url.getFilename() = encodedPath.substring(encodedPath.lastIndexOfAny(listOf("\\", "/")) + 1)

internal fun timestampToOffsetDateTime(timestamp: Long) =
    OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.systemDefault())

internal fun timestampToLocalDate(timestamp: Long) =
    timestampToOffsetDateTime(timestamp).toLocalDate()



private suspend fun getBilibiliImage(
    url: Url,
    name: String,
    refresh: Boolean = false
): File = File(cachePath).resolve("${name}-${url.getFilename()}").apply {
    if (exists().not() || refresh) {
        writeBytes(bilibiliClient.useHttpClient { it.get(url) })
    }
}

internal suspend fun getScreenShot(
    url: String,
    name: String,
    refresh: Boolean = false
): File = File(cachePath).resolve("${name}.png").apply {
    if (exists().not() || refresh) {
        runCatching {
            BilibiliChromeDriverTool(
                remoteAddress = URL(driverUrl),
                chromePath = chromePath,
                deviceName = deviceName
            ).useDriver {
                it.getScreenShot(url, timeoutMillis)
            }
        }.onFailure {
            logger.warning({ "使用ChromeDriver(${driverUrl})失败" }, it)
        }.getOrElse {
            bilibiliClient.useHttpClient {
                it.get("https://www.screenshotmaster.com/api/screenshot") {
                    parameter("url", url)
                    parameter("width", 768)
                    parameter("height", 1024)
                    parameter("zone", "gz")
                    parameter("device", "table")
                    parameter("delay", 500)
                }
            }
        }.let {
            writeBytes(it)
        }
    }
}

internal suspend fun BiliCardInfo.getScreenShot(refresh: Boolean = false) =
    getScreenShot(url = getDynamicUrl(), name = "dynamic-${describe.dynamicId}", refresh = refresh)

internal fun BiliCardInfo.toMessageText(): String = buildString {
    when (describe.type) {
        1 -> {
            BILI_JSON.decodeFromString(BiliReplyCard.serializer(), card).let { card ->
                appendLine("${card.user.uname} -> ${card.originUser.info.uname}: ")
                appendLine(card.item.content)
            }
        }
        2 -> {
            BILI_JSON.decodeFromString(BiliPictureCard.serializer(), card).let { card ->
                appendLine("${card.user.name}: ")
                appendLine(card.item.description)
            }
        }
        4 -> {
            BILI_JSON.decodeFromString(BiliTextCard.serializer(), card).let { card ->
                appendLine("${card.user.uname}: ")
                appendLine(card.item.content)
            }
        }
        8 -> {
            BILI_JSON.decodeFromString(BiliVideoCard.serializer(), card).let { card ->
                appendLine("${card.owner.name}: ")
                appendLine(card.title)
            }
        }
        else -> {
            logger.warning("未知类型卡片")
            appendLine("未知类型卡片")
        }
    }
}

internal fun BiliCardInfo.getDynamicUrl() =
    "https://t.bilibili.com/${describe.dynamicId}"

internal suspend fun BiliCardInfo.getImages(): List<File> = buildList {
    if (describe.type == BiliPictureCard.TYPE) {
        BILI_JSON.decodeFromString(
            deserializer = BiliPictureCard.serializer(),
            string = card
        ).item.pictures.forEachIndexed { index, picture ->
            runCatching {
                getBilibiliImage(url = Url(urlString = picture.imageSource), name = "dynamic-${describe.dynamicId}-${index}")
            }.onSuccess {
                add(it)
            }.onFailure {
                logger.warning({ "动态图片下载失败: ${picture.imageSource}" }, it)
            }
        }
    }
}

internal fun BiliVideoInfo.durationText() =
    "${duration / 3600}:${duration % 3600 / 60}:${duration % 60}"

internal fun BiliVideoInfo.getVideoUrl() =
    "https://www.bilibili.com/video/${bvId}"

internal fun BiliSearchResult.VideoInfo.getVideoUrl() =
    "https://www.bilibili.com/video/${bvId}"

internal suspend fun BiliSearchResult.VideoInfo.getCover(): File =
    getBilibiliImage(url = Url(urlString = picture), name ="video-${bvId}-cover", refresh = false)

internal suspend fun BiliLiveRoom.getCover(): File =
    getBilibiliImage(url = Url(urlString = cover), name = "live-${roomId}-cover", refresh = false)


internal suspend fun BiliVideoInfo.getCover(): File =
    getBilibiliImage(url = Url(urlString = picture), name ="video-${bvId}-cover", refresh = true)
