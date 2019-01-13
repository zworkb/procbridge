package co.gongzh.procbridge

import com.google.gson.JsonObject

import co.gongzh.procbridge.MessageHandler
import co.gongzh.procbridge.ProcBridge
import co.gongzh.procbridge.ProcBridgeException


fun main(args: Array<String>) {

    val host = "127.0.0.1"
    val port = 8077
    val timeout = 10000 // 10 seconds

    println("INFO: make sure that the server is started on Port $port")
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

    val pb = ProcBridge(host, port, timeout, messageHandlerImpl)//.also { it.getClientID() }

    try {
//        ClientKt.pb?.getClientID()
        pb.sendMessage("echo", "{echo:echoooo}")
//        pb.sendMessage("echo", "{[1,2,3]}")
        pb.sendMessage("add", "{elements: [1, 2, 3, 4, 5]}")
//        pb.sendMessage("add", "{elements: [1, 2, 3, 4, 5, 6, 7]}")

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
        Thread.sleep(1000)
    } catch (e: InterruptedException) {
        e.printStackTrace()
    }

    pb.stop()
}