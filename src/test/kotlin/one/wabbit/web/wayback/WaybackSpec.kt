package one.wabbit.web.wayback

import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Ignore
import kotlin.test.Test

class WaybackSpec {
    @Ignore
    @Test fun test() {
        runBlocking {
            println(Wayback.check("http://google.com", Instant.ofEpochSecond(1575167594L), null))

            println(Wayback.check("http://google.com", Instant.ofEpochSecond(0L), null))

            println(Wayback.check("http://google.com", null, null))

            println(Wayback.check("http://does-not-exist-for-sure-a252524123.com", null, null))
        }
    }
}
