package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinator

/**
 * Holder do coordenador das transações remotas — as que o cliente abre e fecha por REST.
 *
 * Populado pelo composition root (backend, testes). Vazio, a API não oferece transação remota: `/api/tx/…`
 * responde erro e toda escrita é uma transação isolada por requisição.
 */
object RemoteTransactions {
    val COORDINATOR = AtomicRef<RemoteTransactionCoordinator>()
}
