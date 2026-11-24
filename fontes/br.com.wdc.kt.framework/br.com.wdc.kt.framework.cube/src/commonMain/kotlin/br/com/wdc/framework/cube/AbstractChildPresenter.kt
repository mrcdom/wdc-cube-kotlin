package br.com.wdc.framework.cube

/** A presenter inside another one, without a place of its own: its owner creates, initializes and releases it. */
abstract class AbstractChildPresenter<A : CubeApplication>(app: A) : AbstractPresenter<A>(app) {

    suspend fun initialize() {
        onInitialize()
    }

    protected abstract suspend fun onInitialize()
}
