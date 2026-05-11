package br.com.wdc.framework.commons.log

fun Log.Companion.getLogger(clazz: Class<*>): Log = getLogger(clazz.name)
