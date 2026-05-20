package br.com.wdc.shopping.view.react

import java.util.concurrent.ThreadFactory

object VirtualThreadFactory {

    fun ofVirtual(namePrefix: String): ThreadFactory {
        return Thread.ofVirtual().name("$namePrefix-", 0).factory()
    }
}
