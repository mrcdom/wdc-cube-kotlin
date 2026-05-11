package br.com.wdc.shopping.persistence.repository.user

import br.com.wdc.framework.commons.util.TransactionContext
import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.persistence.repository.BaseRepository

class UserRepositoryImpl : BaseRepository(), UserRepository {

    override fun insert(user: User): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            InsertRowUserCmd.run(tx.connection(), user)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun insertOrUpdate(user: User): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            if (user.id == null) InsertRowUserCmd.run(tx.connection(), user)
            else UpdateRowUserCmd.run(tx.connection(), user)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun update(newUser: User, oldUser: User): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            UpdateRowUserCmd.run(tx.connection(), newUser, oldUser)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun delete(criteria: UserCriteria): Int = try {
        TransactionContext.begin(dataSource()).use { tx ->
            DeleteUsersCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun count(criteria: UserCriteria): Int = try {
        TransactionContext.begin(dataSource()).use { tx ->
            CountUsersCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun fetch(criteria: UserCriteria): List<User> = try {
        TransactionContext.begin(dataSource()).use { tx ->
            FetchUsersCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun fetchById(userId: Long, projection: User?): User? = try {
        TransactionContext.begin(dataSource()).use { tx ->
            FetchUsersCmd.byId(tx.connection(), userId, projection)
        }
    } catch (e: Exception) {
        readException(e)
    }
}
