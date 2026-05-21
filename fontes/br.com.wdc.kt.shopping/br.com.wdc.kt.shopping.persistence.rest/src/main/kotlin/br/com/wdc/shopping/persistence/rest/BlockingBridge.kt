package br.com.wdc.shopping.persistence.rest

import kotlinx.coroutines.runBlocking

internal fun <T> blocking(block: suspend () -> T): T = runBlocking { block() }
