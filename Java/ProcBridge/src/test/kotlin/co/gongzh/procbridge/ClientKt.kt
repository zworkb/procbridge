package co.gongzh.procbridge

import co.gongzh.procbridge.MessageHandler
import co.gongzh.procbridge.ProcBridge
import co.gongzh.procbridge.ProcBridgeException

import com.google.gson.JsonObject
import org.junit.Test

class ClientKt {
    @Test
    fun test2() {
        println("!!!! Testing Kotlin Client")
    }
}

fun main(args: Array<String>) {

    val host = "127.0.0.1"
    val port = 8877
    val timeout = 10000 // 10 seconds

    val messageHandlerImpl = object : MessageHandler {
        override
        fun onMessage(message: JsonObject) {
            System.out.println("onMessage $message")
        }

        override
        fun onError(e: ProcBridgeException) {
            System.out.println("onError $e")
            e.printStackTrace()
        }
    }

    val pb = ProcBridge(host, port, timeout, messageHandlerImpl)

    try {
        pb.getClientID()
        pb.sendMessage("echo", "{echo:echoooo}")
        pb.sendMessage("add", "{elements: [1, 2, 3, 4, 5]}")

        try {
            pb.sendMessage("retNull", "{}")
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }

        pb.sendMessage("retNullVal", "{}")
    } catch (e: ProcBridgeException) {
        e.printStackTrace()
    }

    //Wait for responses.
    try {
        Thread.sleep(20000)
    } catch (e: InterruptedException) {
        e.printStackTrace()
    }

    pb.stop()
}
