package br.com.wdc.framework.commons.concurrent

import java.util.concurrent.ConcurrentHashMap

actual fun <K, V> concurrentMutableMapOf(): MutableMap<K, V> = ConcurrentHashMap()
