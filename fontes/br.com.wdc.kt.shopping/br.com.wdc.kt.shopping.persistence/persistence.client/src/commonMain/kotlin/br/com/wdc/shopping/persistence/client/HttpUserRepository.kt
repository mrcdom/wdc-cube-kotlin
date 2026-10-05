package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCodec
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserRepository

class HttpUserRepository(
    transport: HttpTransport,
    codec: ModelCodec<User, UserCriteria> = UserCodec(),
) : HttpRepository<User, UserCriteria, Long>(transport, codec, "/api/repo/user"), UserRepository
