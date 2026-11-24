package br.com.wdc.shopping.presentation.presenter.restricted.receipt

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.Scope
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptForm

class ReceiptScope : Scope() {

    var notifySuccess: Boolean by field(false)
    var receipt: ReceiptForm? by field(null)

    override fun write(instanceId: String, json: ExtensibleObjectOutput) {
        json.beginObject()

        json.name("id").value(instanceId)

        json.name("notifySuccess").value(notifySuccess)
        notifySuccess = false

        receipt?.let { r ->
            json.name("receipt").beginObject()

            r.date?.let { json.name("date").value(it) }
            r.total?.let { json.name("total").value(it) }

            json.name("items").beginArray()
            for (receiptItem in r.items) {
                json.beginObject()
                json.name("description").value(receiptItem.description)
                json.name("value").value(receiptItem.value)
                json.name("quantity").value(receiptItem.quantity.toLong())
                json.endObject()
            }
            json.endArray()

            json.endObject()
        }

        json.endObject()
    }
}
