package com.r3corda.demos.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.demos.DemoClock
import com.r3corda.node.internal.Node
import com.r3corda.node.services.network.MockNetworkMapCache
import java.time.LocalDate

/**
 * This is a less temporary, demo-oriented way of initiating processing of temporal events
 */
object UpdateBusinessDayProtocol {

    val TOPIC = "businessday.topic"

    data class UpdateBusinessDayMessage(val date: LocalDate)

    object Handler {

        fun register(node: Node) {
            node.net.addMessageHandler("${TOPIC}.0") { msg, registration ->
                val updateBusinessDayMessage = msg.data.deserialize<UpdateBusinessDayMessage>()
                (node.services.clock as DemoClock).updateDate(updateBusinessDayMessage.date)
            }
        }
    }

    class Broadcast(val date: LocalDate,
                    override val progressTracker: ProgressTracker = Broadcast.tracker()) : ProtocolLogic<Unit>() {

        companion object {
            object NOTIFYING : ProgressTracker.Step("Notifying peers")

            fun tracker() = ProgressTracker(NOTIFYING)
        }

        @Suspendable
        override fun call(): Unit {
            progressTracker.currentStep = NOTIFYING
            val message = UpdateBusinessDayMessage(date)
            for (recipient in serviceHub.networkMapCache.partyNodes) {
                doNextRecipient(recipient, message)
            }
        }

        @Suspendable
        private fun doNextRecipient(recipient: NodeInfo, message: UpdateBusinessDayMessage) {
            if (recipient.address is MockNetworkMapCache.MockAddress) {
                // Ignore
            } else {
                send(TOPIC, recipient.address, 0, message)
            }
        }
    }

}