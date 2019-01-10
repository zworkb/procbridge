package co.gongzh.procbridge

import co.gongzh.procbridge.MessageHandler
import co.gongzh.procbridge.ProcBridge
import co.gongzh.procbridge.ProcBridgeException

import com.google.gson.JsonObject


fun main(args: Array<String>) {

    val host = "127.0.0.1"
    val port = 8877
    val timeout = 10000 // 10 seconds

    val server = makeServer(port = port)

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

    val pb = ProcBridge(host, port, timeout, messageHandlerImpl).also { it.getClientID() }

    try {
//        ClientKt.pb?.getClientID()
        pb.sendMessage("echo", "{echo:echoooo}")
//        pb.sendMessage("echo", "{[1,2,3]}")
        pb.sendMessage("add", "{elements: [1, 2, 3, 4, 5]}")

//        try {
//            pb.sendMessage("retNull", "{}")
//        } catch (e: RuntimeException) {
//            e.printStackTrace()
//        }
//
//        pb.sendMessage("retNullVal", "{}")
    } catch (e: ProcBridgeException) {
        e.printStackTrace()
    }

    //Wait for responses.
    try {
        Thread.sleep(2000)
    } catch (e: InterruptedException) {
        e.printStackTrace()
    }

    println("stopping server...")
    server.stop()
    println("server stopped")
    println("stopping bridge...")
    pb.stop()
    println("bridge stopped")
}
