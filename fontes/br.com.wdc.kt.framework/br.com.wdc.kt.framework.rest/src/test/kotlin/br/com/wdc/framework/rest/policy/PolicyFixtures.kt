package br.com.wdc.framework.rest.policy

import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.TextCriterion
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.rest.security.RestPrincipal
import br.com.wdc.framework.rest.security.SimplePrincipal

// A neutral domain for the policy tests: customers, their orders and the order lines.

class Customer {
    var id: Long? = null
    var login: String? = null
    var name: String? = null
    var secret: String? = null
}

class CustomerCriteria : Criteria {
    var projection: Customer? = null
    fun withProjection(p: Customer?) = apply { projection = p }

    val customerId = ComparableCriterion<CustomerCriteria, Long>(this, "customerId")
    val login = TextCriterion(this, "login")
    val name = TextCriterion(this, "name")
    override fun criterions(): List<Criterion<*, *>> = listOf(customerId, login, name)

    var orderBy: OrderBy? = null
    enum class OrderBy { OLDEST_FIRST, BY_NAME }

    override var anyOf: List<CustomerCriteria> = emptyList()
        private set
    fun withAnyOf(groups: List<CustomerCriteria>) = apply { anyOf = groups }
    fun withAnyOf(vararg groups: CustomerCriteria) = withAnyOf(groups.asList())
}

class Order {
    var id: Long? = null
    var status: String? = null
    var customer: Customer? = null
    var lines: MutableList<OrderLine>? = null
}

class OrderCriteria : Criteria {
    var projection: Order? = null
    fun withProjection(p: Order?) = apply { projection = p }

    val orderId = ComparableCriterion<OrderCriteria, Long>(this, "orderId")
    val customerId = ComparableCriterion<OrderCriteria, Long>(this, "customerId")
    val status = TextCriterion(this, "status")
    override fun criterions(): List<Criterion<*, *>> = listOf(orderId, customerId, status)

    override var anyOf: List<OrderCriteria> = emptyList()
        private set
    fun withAnyOf(groups: List<OrderCriteria>) = apply { anyOf = groups }
}

class OrderLine {
    var id: Long? = null
    var quantity: Int? = null
    var order: Order? = null
}

class OrderLineCriteria : Criteria {
    var projection: OrderLine? = null
    fun withProjection(p: OrderLine?) = apply { projection = p }

    val customerId = ComparableCriterion<OrderLineCriteria, Long>(this, "customerId")
    override fun criterions(): List<Criterion<*, *>> = listOf(customerId)

    override var anyOf: List<OrderLineCriteria> = emptyList()
        private set
    fun withAnyOf(groups: List<OrderLineCriteria>) = apply { anyOf = groups }
}

val PV = ProjectionValues

const val ME = 7L

fun me(vararg roles: String): RestPrincipal = SimplePrincipal(ME.toString(), roles.toSet())

val CUSTOMER = EntityDescriptor("customer", ::Customer, ::CustomerCriteria) {
    projection(CustomerCriteria::projection, CustomerCriteria::withProjection)
    anyOf(CustomerCriteria::anyOf, CustomerCriteria::withAnyOf)
    key(Customer::id, PV.i64)
    value(Customer::login, PV.str)
    value(Customer::name, PV.str)
    writeOnly(Customer::secret, PV.str)
    criterion("customerId", Customer::id)
    orderings { c -> if (c.orderBy == CustomerCriteria.OrderBy.BY_NAME) setOf(Customer::name) else emptySet() }
}

/** The requester's own customer row. */
object OwnCustomer : RowScope<Customer, CustomerCriteria> {
    override fun restrict(group: CustomerCriteria, requester: RestPrincipal) {
        group.customerId.eq(requester.id.toLong())
    }

    override suspend fun covers(row: Customer, requester: RestPrincipal) = row.id == requester.id.toLong()
}

/** Rows named "Team" — a scope that is not about the key, to test moving a row out of reach. */
object Team : RowScope<Customer, CustomerCriteria> {
    override fun restrict(group: CustomerCriteria, requester: RestPrincipal) {
        group.name.eq("Team")
    }

    override suspend fun covers(row: Customer, requester: RestPrincipal) = row.name == "Team"
    override fun projection() = Customer().apply { name = PV.str }
}

val CUSTOMER_POLICY: DataPolicy<Customer, CustomerCriteria> = DataPolicy(CUSTOMER) {
    grant("ADMIN") {
        read(Customer::login, Customer::name)
        create(Customer::login, Customer::name, Customer::secret)
        update(Customer::login, Customer::name, Customer::secret)
        delete()
    }
    grant("CLIENT") {
        rows(OwnCustomer)
        read(Customer::login, Customer::name)
    }
}

val ORDER: EntityDescriptor<Order, OrderCriteria> = EntityDescriptor("order", ::Order, ::OrderCriteria) {
    projection(OrderCriteria::projection, OrderCriteria::withProjection)
    anyOf(OrderCriteria::anyOf, OrderCriteria::withAnyOf)
    key(Order::id, PV.i64)
    value(Order::status, PV.str)
    relation(Order::customer) { CUSTOMER_POLICY }
    collection(Order::lines, inverse = OrderLine::order) { LINE_POLICY }
    criterion("orderId", Order::id)
    criterion("customerId", Order::customer)
    defaultShape {
        Order().apply {
            status = PV.str
            customer = Customer().apply { name = PV.str; login = PV.str }
            lines = PV.singletonList(OrderLine().apply { quantity = PV.i32 }, null)
        }
    }
}

object OwnOrders : RowScope<Order, OrderCriteria> {
    override fun restrict(group: OrderCriteria, requester: RestPrincipal) {
        group.customerId.eq(requester.id.toLong())
    }

    override suspend fun covers(row: Order, requester: RestPrincipal) = row.customer?.id == requester.id.toLong()
    override fun projection() = Order().apply { customer = Customer().apply { id = PV.i64 } }
}

val ORDER_POLICY: DataPolicy<Order, OrderCriteria> = DataPolicy(ORDER) {
    grant("ADMIN") {
        read(Order::status, Order::customer, Order::lines)
        create(Order::status, Order::customer, Order::lines)
        update(Order::status, Order::customer)
        delete()
    }
    grant("CLIENT") {
        rows(OwnOrders)
        read(Order::status, Order::customer, Order::lines)
        create(Order::status, Order::customer, Order::lines)
    }
}

val LINE: EntityDescriptor<OrderLine, OrderLineCriteria> = EntityDescriptor("order-line", ::OrderLine, ::OrderLineCriteria) {
    projection(OrderLineCriteria::projection, OrderLineCriteria::withProjection)
    anyOf(OrderLineCriteria::anyOf, OrderLineCriteria::withAnyOf)
    key(OrderLine::id, PV.i64)
    value(OrderLine::quantity, PV.i32)
    relation(OrderLine::order) { ORDER_POLICY }
    criterion("customerId", OrderLine::order)
}

object OwnLines : RowScope<OrderLine, OrderLineCriteria> {
    override fun restrict(group: OrderLineCriteria, requester: RestPrincipal) {
        group.customerId.eq(requester.id.toLong())
    }

    override suspend fun covers(row: OrderLine, requester: RestPrincipal) = row.order?.customer?.id == requester.id.toLong()
    override fun projection() = OrderLine().apply { order = Order().apply { customer = Customer().apply { id = PV.i64 } } }
}

val LINE_POLICY: DataPolicy<OrderLine, OrderLineCriteria> = DataPolicy(LINE) {
    grant("ADMIN") {
        read(OrderLine::quantity, OrderLine::order)
        create(OrderLine::quantity, OrderLine::order)
        delete()
    }
    grant("CLIENT") {
        rows(OwnLines)
        read(OrderLine::quantity, OrderLine::order)
        create(OrderLine::quantity, OrderLine::order)
    }
}
