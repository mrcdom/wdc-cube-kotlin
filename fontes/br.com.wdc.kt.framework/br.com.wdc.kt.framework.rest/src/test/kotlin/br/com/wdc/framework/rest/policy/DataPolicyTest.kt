package br.com.wdc.framework.rest.policy

import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.NotFoundException
import br.com.wdc.framework.domain.projection.ProjectionList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The rules of [DataAccess], on policies written for the test — no database: what is checked is how a request is
 * refused or rewritten, and how a row is shaped.
 */
class DataPolicyTest {

    private fun login() = Customer().apply { login = PV.str; name = PV.str }

    private fun policy(declare: DataPolicy.Builder<Customer, CustomerCriteria>.() -> Unit) = DataPolicy(CUSTOMER, declare)

    // :: Filtering

    @Test
    fun filteringByAFieldNoGrantReads_isRefusedByName() {
        val policy = policy { grant("R") { read(Customer::name) } }
        val e = assertFailsWith<AccessDeniedException> {
            policy.accessFor(me("R")).prepareRead(CustomerCriteria().login.startingWith("a"))
        }
        assertTrue("[login]" in e.message!!, e.message)
    }

    @Test
    fun filteringByTwoFieldsThatNoSingleGrantReads_isRefused() {
        // each field is read by one grant, over different rows: filtering by both at once would reveal the pair
        val policy = policy {
            grant("R") { read(Customer::name) }
            grant("R") { rows(OwnCustomer); read(Customer::login) }
        }
        val criteria = CustomerCriteria().name.eq("x").login.eq("y")
        val e = assertFailsWith<AccessDeniedException> { policy.accessFor(me("R")).prepareRead(criteria) }
        assertTrue("login" in e.message!! && "name" in e.message!!, e.message)
    }

    @Test
    fun filteringInsideAnAlternativeGroup_isCheckedToo() {
        val policy = policy { grant("R") { read(Customer::name) } }
        val criteria = CustomerCriteria().withAnyOf(CustomerCriteria().login.containing("adm"))
        assertFailsWith<AccessDeniedException> { policy.accessFor(me("R")).prepareRead(criteria) }
    }

    @Test
    fun sortingCountsAsReading() {
        val policy = policy { grant("R") { read(Customer::login) } }
        val criteria = CustomerCriteria().apply { orderBy = CustomerCriteria.OrderBy.BY_NAME }
        val e = assertFailsWith<AccessDeniedException> { policy.accessFor(me("R")).prepareRead(criteria) }
        assertTrue("[name]" in e.message!!, e.message)
        // an ordering by the key reveals nothing
        policy.accessFor(me("R")).prepareRead(CustomerCriteria().apply { orderBy = CustomerCriteria.OrderBy.OLDEST_FIRST })
    }

    // :: Rows

    @Test
    fun aGrantOverEveryRow_addsNoRestriction() {
        val policy = policy {
            grant("R") { rows(OwnCustomer); read(Customer::login, Customer::name) }
            grant("R") { read(Customer::login, Customer::name) }
        }
        val criteria = CustomerCriteria()
        policy.accessFor(me("R")).prepareRead(criteria)
        assertTrue(criteria.anyOf.isEmpty())
    }

    @Test
    fun scopedGrants_becomeAlternativeGroups_carryingTheServerGroups() {
        val policy = policy {
            grant("A") { rows(OwnCustomer); read(Customer::login, Customer::name) }
            grant("B") { rows(Team); read(Customer::login, Customer::name) }
        }
        val serverGroup = CustomerCriteria().login.eq("jdoe")
        val criteria = CustomerCriteria().withAnyOf(serverGroup)
        policy.accessFor(me("A", "B")).prepareRead(criteria)

        assertEquals(2, criteria.anyOf.size, "one group per scope")
        for (group in criteria.anyOf) {
            assertEquals(listOf(serverGroup), group.anyOf, "the alternatives already there hold inside each scope")
        }
        assertTrue(criteria.anyOf.any { it.customerId.isSet() })
        assertTrue(criteria.anyOf.any { it.name.isSet() })
    }

    @Test
    fun aGrantSharedByTwoRoles_countsOnce() {
        val policy = policy { grant("A", "B") { rows(OwnCustomer); read(Customer::name) } }
        val criteria = CustomerCriteria()
        policy.accessFor(me("A", "B")).prepareRead(criteria)
        assertEquals(1, criteria.anyOf.size)
    }

    @Test
    fun theRowCheckFields_areFetched_butNotReturned() = runBlocking {
        val policy = policy { grant("R") { rows(Team); read(Customer::login) } }
        val criteria = CustomerCriteria().withProjection(Customer().apply { login = PV.str })
        val plan = policy.accessFor(me("R")).prepareRead(criteria)

        assertEquals("~", criteria.projection!!.name, "name is fetched for the row check")
        val row = plan.apply(Customer().apply { id = 1L; login = "jdoe"; name = "Team" })
        assertEquals("jdoe", row.login)
        assertNull(row.name, "and cleared before the response")
    }

    @Test
    fun eachRow_showsWhatTheGrantsThatReachItAllow() = runBlocking {
        val policy = policy {
            grant("R") { rows(OwnCustomer); read(Customer::login, Customer::name) }
            grant("R") { read(Customer::name); mask(Mask.keepLast(2), Customer::login) }
        }
        val plan = policy.accessFor(me("R")).prepareRead(CustomerCriteria().withProjection(login()))

        val own = plan.apply(Customer().apply { id = ME; login = "myself"; name = "Me" })
        val other = plan.apply(Customer().apply { id = 1L; login = "someone"; name = "Other" })

        assertEquals("myself", own.login)
        assertEquals("*****ne", other.login)
        assertEquals("Other", other.name)
    }

    @Test
    fun aRowNoGrantReaches_keepsOnlyItsKey() = runBlocking {
        val policy = policy { grant("R") { rows(OwnCustomer); read(Customer::login, Customer::name) } }
        val plan = policy.accessFor(me("R")).prepareRead(CustomerCriteria().withProjection(login()))
        val other = plan.apply(Customer().apply { id = 1L; login = "x"; name = "X" })
        assertEquals(1L, other.id)
        assertNull(other.login)
        assertNull(other.name)
    }

    @Test
    fun aRoleWithoutGrants_readsNothing() {
        val e = assertFailsWith<AccessDeniedException> { CUSTOMER_POLICY.accessFor(me("GUEST")).prepareRead(CustomerCriteria()) }
        assertTrue("customer" in e.message!!, e.message)
    }

    // :: Projection

    @Test
    fun projectingAFieldNoGrantReads_isRefusedByName() {
        val e = assertFailsWith<AccessDeniedException> {
            CUSTOMER_POLICY.accessFor(me("ADMIN")).prepareRead(CustomerCriteria().withProjection(Customer().apply { name = PV.str; secret = PV.str }))
        }
        assertTrue("[secret]" in e.message!!, e.message)
    }

    @Test
    fun withoutProjection_theDefaultShapeIsTrimmed_notRefused() {
        val policy = policy { grant("R") { read(Customer::name) } }
        // the default shape has every field but the write-only ones — the login included, which this role does not
        // read. The client asked for nothing in particular, so nothing it asked for is refused.
        val criteria = CustomerCriteria()
        policy.accessFor(me("R")).prepareRead(criteria)
        assertEquals("~", criteria.projection!!.name)
        assertNull(criteria.projection!!.login)
        assertNull(criteria.projection!!.secret)
    }

    @Test
    fun insideARelation_theRelatedPolicyDecides() = runBlocking {
        val access = ORDER_POLICY.accessFor(me("CLIENT"))
        // the order reads its customer, and the customer policy lets a client read its own login
        val criteria = OrderCriteria().withProjection(Order().apply { status = PV.str; customer = Customer().apply { login = PV.str } })
        val plan = access.prepareRead(criteria)
        assertEquals(1, criteria.anyOf.size, "a client reaches its own orders")
        val row = plan.apply(Order().apply { id = 1L; status = "open"; customer = Customer().apply { id = ME; login = "me"; name = "Me" } })
        assertEquals("me", row.customer!!.login)
        assertNull(row.customer!!.name, "not asked")

        // the secret is out of every grant: asking for it inside the relation is refused too
        val e = assertFailsWith<AccessDeniedException> {
            access.prepareRead(OrderCriteria().withProjection(Order().apply { customer = Customer().apply { secret = PV.str } }))
        }
        assertTrue("[secret]" in e.message!!, e.message)
    }

    @Test
    fun aCollection_isRestrictedAndShapedByItsPolicy() = runBlocking {
        val criteria = OrderCriteria()
        val plan = ORDER_POLICY.accessFor(me("CLIENT")).prepareRead(criteria)

        val fetched = criteria.projection!!.lines as ProjectionList<OrderLine>
        val lineCriteria = fetched.criteria as OrderLineCriteria
        assertEquals(1, lineCriteria.anyOf.size, "the lines of the default shape are restricted by their own policy")
        assertNotNull(fetched.first().order?.customer?.id, "and fetch what their row check reads")

        val row = Order().apply {
            id = 1L
            status = "open"
            customer = Customer().apply { id = ME; name = "Me"; login = "me" }
            lines = mutableListOf(OrderLine().apply { id = 10L; quantity = 2; order = Order().apply { id = 1L; customer = Customer().apply { id = ME } } })
        }
        plan.apply(row)
        assertEquals(2, row.lines!!.single().quantity)
        assertNull(row.lines!!.single().order, "the row check's fields leave the response")
        assertEquals("Me", row.customer!!.name)
    }

    // :: Writing

    @Test
    fun anUpdateThatMovesTheRowOutOfReach_isRefused() = runBlocking {
        val policy = policy { grant("R") { rows(Team); read(Customer::login, Customer::name); update(Customer::login, Customer::name) } }
        val access = policy.accessFor(me("R"))
        val stored = Customer().apply { id = 1L; name = "Team" }

        // within reach: allowed
        access.checkUpdate(Customer().apply { id = 1L; login = "renamed" }, Customer().apply { login = PV.str }) { _, _ -> stored }

        // out of it: refused
        assertFailsWith<AccessDeniedException> {
            access.checkUpdate(Customer().apply { id = 1L; name = "Elsewhere" }, Customer().apply { name = PV.str }) { _, _ -> stored }
        }
        Unit
    }

    @Test
    fun aFieldOutsideTheGrant_cannotBeUpdated() = runBlocking {
        val policy = policy { grant("R") { rows(OwnCustomer); read(Customer::login, Customer::name); update(Customer::name) } }
        val e = assertFailsWith<AccessDeniedException> {
            policy.accessFor(me("R")).checkUpdate(Customer().apply { id = ME; login = "admin" }, Customer().apply { login = PV.str }) { _, _ ->
                Customer().apply { id = ME }
            }
        }
        assertTrue("[login]" in e.message!!, e.message)
    }

    @Test
    fun anUpdateOfARowOutOfReach_isNotFound_unlessItIsReadable() = runBlocking {
        val policy = policy {
            grant("R") { rows(OwnCustomer); read(Customer::name); update(Customer::name) }
            grant("R") { rows(Team); read(Customer::name) }
        }
        val access = policy.accessFor(me("R"))
        val change = Customer().apply { name = PV.str }
        assertFailsWith<NotFoundException> {
            access.checkUpdate(Customer().apply { id = 1L; name = "x" }, change) { _, _ -> Customer().apply { id = 1L; name = "Other" } }
        }
        // a row the requester reads, but does not update: refused, since its existence is no secret
        assertFailsWith<AccessDeniedException> {
            access.checkUpdate(Customer().apply { id = 2L; name = "x" }, change) { _, _ -> Customer().apply { id = 2L; name = "Team" } }
        }
        // without the key
        assertFailsWith<InvalidRequestException> { access.checkUpdate(Customer().apply { name = "x" }, change) { _, _ -> null } }
        Unit
    }

    @Test
    fun aRoleThatDoesNotUpdate_orDelete_isRefused() {
        val access = CUSTOMER_POLICY.accessFor(me("CLIENT"))
        assertFailsWith<AccessDeniedException> {
            runBlocking { access.checkUpdate(Customer().apply { id = ME; name = "x" }, Customer().apply { name = PV.str }) { _, _ -> null } }
        }
        assertFailsWith<AccessDeniedException> { access.restrictDelete(CustomerCriteria().customerId.eq(ME)) }
    }

    @Test
    fun create_needsOneGrantForEveryField_andReachingTheNewRow() = runBlocking {
        val access = ORDER_POLICY.accessFor(me("CLIENT"))
        access.checkCreate(Order().apply { status = "new"; customer = Customer().apply { id = ME } })

        // on someone else's behalf: the grant does not reach the new row
        assertFailsWith<AccessDeniedException> { access.checkCreate(Order().apply { status = "new"; customer = Customer().apply { id = 1L } }) }
        // a relation is written by its key only
        val e = assertFailsWith<AccessDeniedException> {
            access.checkCreate(Order().apply { status = "new"; customer = Customer().apply { id = ME; name = "Renamed" } })
        }
        assertTrue("written by its key only" in e.message!!, e.message)
        Unit
    }

    @Test
    fun create_checksTheRowsOfACollection_asRowsOfTheNewOne() = runBlocking {
        val access = ORDER_POLICY.accessFor(me("CLIENT"))
        val line = OrderLine().apply { quantity = 1 }
        val order = Order().apply { status = "new"; customer = Customer().apply { id = ME }; lines = mutableListOf(line) }
        // the line belongs to the new order while it is checked: the line policy reaches it through the order's customer
        access.checkCreate(order)
        assertNull(line.order, "what the line carried is given back")

        // a line sent on its own, into an order the scope does not see as the requester's, is not reached
        assertFailsWith<AccessDeniedException> {
            LINE_POLICY.accessFor(me("CLIENT")).checkCreate(OrderLine().apply { quantity = 1; this.order = Order().apply { id = 9L } })
        }
        Unit
    }

    @Test
    fun readBack_dropsWhatTheRequesterDoesNotRead() = runBlocking {
        val written = Customer().apply { id = 5L; login = "new"; name = "New"; secret = "digest" }
        CUSTOMER_POLICY.accessFor(me("ADMIN")).readBack(written)
        assertEquals("new", written.login)
        assertNull(written.secret)

        // a requester that reads nothing of the entity gets the key only
        val other = Customer().apply { id = 6L; login = "x"; name = "X" }
        CUSTOMER_POLICY.accessFor(me("GUEST")).readBack(other)
        assertEquals(6L, other.id)
        assertNull(other.login)
    }

    @Test
    fun readBack_followsAGraphWithBackReferences() = runBlocking {
        val order = Order().apply { id = 1L; status = "new"; customer = Customer().apply { id = ME } }
        order.lines = mutableListOf(OrderLine().apply { id = 2L; quantity = 1; this.order = order })
        ORDER_POLICY.accessFor(me("CLIENT")).readBack(order)
        assertEquals("new", order.status)
        assertEquals(1, order.lines!!.single().quantity)
        assertSame(order, order.lines!!.single().order)
    }

    @Test
    fun delete_isRestrictedToTheRowsOfTheGrantsThatDelete() {
        val policy = policy {
            grant("R") { rows(OwnCustomer); read(Customer::name); delete() }
            grant("R") { read(Customer::name) }
        }
        val criteria = CustomerCriteria().name.eq("x")
        policy.accessFor(me("R")).restrictDelete(criteria)
        assertEquals(1, criteria.anyOf.size)
        assertTrue(criteria.anyOf.single().customerId.isSet())
    }

    // :: Declaration

    @Test
    fun aMaskedFieldCannotBeWrittenByTheSameGrant() {
        assertFailsWith<IllegalStateException> {
            policy { grant("R") { mask(Mask.keepFirst(1), Customer::login); update(Customer::login) } }
        }
    }

    @Test
    fun theKeyCannotBeMasked_norAWriteOnlyFieldRead() {
        assertFailsWith<IllegalStateException> { policy { grant("R") { mask(Mask.keepFirst(1), Customer::id) } } }
        assertFailsWith<IllegalStateException> { policy { grant("R") { read(Customer::secret) } } }
    }

    @Test
    fun aCriteriaFieldWithoutAnEntityField_failsWhenTheDescriptorIsBuilt() {
        val e = assertFailsWith<IllegalStateException> {
            EntityDescriptor("customer", ::Customer, ::CustomerCriteria) {
                projection(CustomerCriteria::projection, CustomerCriteria::withProjection)
                anyOf(CustomerCriteria::anyOf, CustomerCriteria::withAnyOf)
                key(Customer::id, PV.i64)
                value(Customer::login, PV.str)
                value(Customer::name, PV.str)
            }
        }
        assertTrue("customerId" in e.message!!, e.message)
    }

    @Test
    fun masks() {
        assertEquals("j***", Mask.keepFirst(1).apply("jdoe"))
        assertEquals("*********09", Mask.keepLast(2).apply("12345678909"))
        assertEquals("ab", Mask.keepFirst(5).apply("ab"))
    }

    // :: Security off

    @Test
    fun withoutSecurity_everyRowAndField_butTheWriteOnlyOnes() = runBlocking {
        val access = CUSTOMER_POLICY.accessFor(null)
        val criteria = CustomerCriteria().login.eq("jdoe")
        val plan = access.prepareRead(criteria)
        assertTrue(criteria.anyOf.isEmpty())
        assertNull(criteria.projection, "without a projection, the repository's own default")
        val row = plan.apply(Customer().apply { id = 1L; login = "jdoe"; name = "J"; secret = "digest" })
        assertEquals("jdoe", row.login)
        assertNull(row.secret, "a write-only field never goes out")

        val asked = CustomerCriteria().withProjection(Customer().apply { name = PV.str })
        access.prepareRead(asked)
        assertEquals("~", asked.projection!!.name)

        val e = assertFailsWith<AccessDeniedException> {
            access.prepareRead(CustomerCriteria().withProjection(Customer().apply { secret = PV.str }))
        }
        assertTrue("[secret]" in e.message!!, e.message)

        // writes are not checked
        access.checkCreate(Customer().apply { secret = "x" })
        access.checkUpdate(Customer().apply { id = 1L; secret = "x" }, Customer().apply { secret = PV.str }) { _, _ -> error("not loaded") }
        access.restrictDelete(CustomerCriteria().customerId.eq(1L))
    }
}
