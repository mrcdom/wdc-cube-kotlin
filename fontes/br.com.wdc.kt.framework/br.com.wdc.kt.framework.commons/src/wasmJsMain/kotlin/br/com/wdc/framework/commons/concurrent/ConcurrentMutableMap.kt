package br.com.wdc.framework.commons.concurrent

actual fun <K, V> concurrentMutableMapOf(): MutableMap<K, V> = LinkedHashMap()
