package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCodec
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserRepository

class HttpUserRepository(
    transport: HttpTransport,
    codec: ModelCodec<User, UserCriteria> = UserCodec(),
) : HttpRepository<User, UserCriteria, Long>(transport, codec, "/api/repo/user"), UserRepository {

    override fun fill(target: User, stored: User) = fillUser(target, stored)
}

/** A senha nunca vem do servidor: a do bean fica como estava. */
internal fun fillUser(target: User, stored: User) {
    stored.id?.let { target.id = it }
    stored.userName?.let { target.userName = it }
    stored.name?.let { target.name = it }
    stored.roles?.let { target.roles = it }
}
