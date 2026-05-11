package br.com.wdc.shopping.domain.utils

import br.com.wdc.shopping.domain.model.PlatformDateTime
import java.time.OffsetDateTime

internal actual fun platformDateTimeProjectionValue(): PlatformDateTime = OffsetDateTime.MIN
