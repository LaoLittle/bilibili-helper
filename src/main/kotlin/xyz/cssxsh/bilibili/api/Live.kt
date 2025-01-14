package xyz.cssxsh.bilibili.api

import io.ktor.client.request.*
import xyz.cssxsh.bilibili.*
import xyz.cssxsh.bilibili.data.*

suspend fun BiliClient.getRoomInfo(
    roomId: Long,
    url: String = ROOM_INIT
): BiliRoomInfo = json(url) {
    parameter("id", roomId)
}

suspend fun BiliClient.getRoomInfoOld(
    uid: Long,
    url: String = ROOM_INFO_OLD
): BiliRoomSimple = json(url) {
    parameter("mid", uid)
}

suspend fun BiliClient.getOffLiveList(
    roomId: Long,
    count: Int,
    timestamp: Long = System.currentTimeMillis() / 1_000,
    url: String = ROOM_OFF_LIVE
): BiliLiveOff = json(url) {
    parameter("room_id", roomId)
    parameter("count", count)
    parameter("rnd", timestamp)
}

suspend fun BiliClient.getRoundPlayVideo(
    roomId: Long,
    timestamp: Long = System.currentTimeMillis() / 1_000,
    type: String = "flv",
    url: String = ROOM_ROUND_PLAY
): BiliRoundPlayVideo = json(url) {
    parameter("room_id", roomId)
    parameter("a", timestamp)
    parameter("type", type)
}