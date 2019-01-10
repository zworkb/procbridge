package co.gongzh.procbridge

import co.gongzh.procbridge.MessageHandler
import co.gongzh.procbridge.ProcBridge
import co.gongzh.procbridge.ProcBridgeException

import com.google.gson.JsonObject
import org.junit.Test

class ClientKt {

    @Test
    fun test_java_server() {
        println("!!!! Testing Kotlin Client, $pb")
        pb.sendMessage("echo", "Hi Back")
    }

    @Test
    fun test3() {
        println("!!!! Testing 3")
    }

    companion object {
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

        var server:ProcBridgeServer? = makeServer(port=port).also{
            println("server created here")
        }
        var pb:ProcBridge = ProcBridge(host, port, timeout, messageHandlerImpl)
                .also {it.getClientID()}
    }
}

class Delegate {
    @APIHandler
    internal fun echo(arg: JsonObject): JsonObject {
        println("arg received: $arg")
        return arg
    }

    @APIHandler
    internal fun add(arg: JsonObject): JsonObject {
        val elements = arg.get("elements").asJsonArray
        var sum = 0
        for (i in 0 until elements.size()-1) {
            sum += elements.get(i).asInt
        }
        val result = JsonObject()
        result.addProperty("result", sum)
        return result
    }
}

fun makeServer(port: Int = 8877) : ProcBridgeServer {
    println("starting server in makeServer")
    val server = ProcBridgeServer(port)

    server.setDelegate(Delegate())
    server.start()
    return server
}

fun main(args: Array<String>) {

    val host = "127.0.0.1"
    val port = 8877
    val timeout = 10000 // 10 seconds

//    ClientKt.server = makeServer(port=port)

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

//    val pb = ProcBridge(host, port, timeout, messageHandlerImpl)

//    try {
//        ClientKt.pb?.getClientID()
//        pb.sendMessage("echo", "{echo:echoooo}")
//        pb.sendMessage("add", "{elements: [1, 2, 3, 4, 5]}")
//
//        try {
//            pb.sendMessage("retNull", "{}")
//        } catch (e: RuntimeException) {
//            e.printStackTrace()
//        }
//
//        pb.sendMessage("retNullVal", "{}")
//    } catch (e: ProcBridgeException) {
//        e.printStackTrace()
//    }

    //Wait for responses.
    try {
        Thread.sleep(200000)
    } catch (e: InterruptedException) {
        e.printStackTrace()
    }

    ClientKt.pb?.stop()
}
