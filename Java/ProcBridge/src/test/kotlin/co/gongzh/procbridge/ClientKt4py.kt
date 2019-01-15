package co.gongzh.procbridge

import com.google.gson.JsonObject


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
        val echo=pb.sendMessage("echo", "{echo:echoooo}")
        println("calling add:")
        pb.sendMessage("add", "{elements: [1, 2, 3, 4, 5]}")
        println("calling add:")
        pb.sendMessage("add", "{elements: [1, 2, 3, 4, 5, 6, 7]}")
        println("calling gettime:")
        pb.sendMessage("gettime")
        println("calling add sync:")
        val res: JsonObject = pb.sendMessage("add", "{elements:[1,2,3]}").get()
        println("res=$res")
//        val res1 = pb.sendMessage("geterror")//.get()
//        res1.get()
//        println("res1.get:${res1.get()}")

        pb.sendMessage("shutdown")

    } catch (e: ProcBridgeException) {
        e.printStackTrace()
    }

    //Wait for responses.
//    try {
//        Thread.sleep(1000)
//    } catch (e: InterruptedException) {
//        e.printStackTrace()
//    }

    pb.stop()
}
