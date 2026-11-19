package io.trtc.tuikit.chat.demo.xingdun.network

import org.junit.Assert.assertEquals
import org.junit.Test

class XingDunAvatarUrlTest {
    @Test fun migratesManagedUserAndGroupImagesWithoutChangingThePathOrQuery() {
        for (kind in listOf("avatar", "group")) {
            val suffix = "/upload/im/$kind/20261101/01_photo.jpg?v=2&token=a%2Fb"
            assertEquals("https://api.xuyinfang365.bid$suffix",
                XingDunAvatarUrl.resolve(" https://API.XINGDUNIM.COM:443$suffix "))
        }
    }

    @Test fun leavesOtherHostsPathsAndLocalImagesAlone() {
        for (url in listOf(
            "https://api.xuyinfang365.bid/upload/im/avatar/photo.jpg",
            "https://cdn.example.com/upload/im/avatar/photo.jpg",
            "https://api.xingdunim.com/upload/im/chat/photo.jpg",
            "https://api.xingdunim.com/upload/im/avatar",
            "content://media/external/images/media/42",
            "file:///android_asset/tuxing/avatar.png",
            "",
        )) {
            assertEquals(url, XingDunAvatarUrl.resolve(url))
        }
    }

    @Test fun neverRewritesDeceptiveOrUnmanagedAuthoritiesAndPaths() {
        for (url in listOf(
            "http://api.xingdunim.com/upload/im/avatar/photo.jpg",
            "https://api.xingdunim.com.evil.example/upload/im/avatar/photo.jpg",
            "https://api.xingdunim.com:8443/upload/im/avatar/photo.jpg",
            "https://api.xingdunim.com@evil.example/upload/im/avatar/photo.jpg",
            "https://someone@api.xingdunim.com/upload/im/avatar/photo.jpg",
            "https://api.xingdunim.com/upload/im/avatar/../photo.jpg",
            "https://api.xingdunim.com/upload/im/avatar/%2e%2e/photo.jpg",
            "https://api.xingdunim.com/upload/im/avatar/photo.jpg\nother",
        )) {
            assertEquals(url, XingDunAvatarUrl.resolve(url))
        }
    }
}
