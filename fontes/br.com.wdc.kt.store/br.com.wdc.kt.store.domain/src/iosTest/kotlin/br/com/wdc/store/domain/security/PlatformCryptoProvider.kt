package br.com.wdc.store.domain.security

actual fun platformCryptoProvider(): CryptoProvider = IosCryptoProvider()
