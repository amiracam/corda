package net.corda.bank.api

import net.corda.core.contracts.Amount
import net.corda.core.contracts.currency
import net.corda.core.flows.FlowException
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.loggerFor
import net.corda.flows.IssuerFlow.IssuanceRequester
import org.bouncycastle.asn1.x500.X500Name
import java.time.LocalDateTime
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

// API is accessible from /api/bank. All paths specified below are relative to it.
@Path("bank")
class BankOfCordaWebApi(val rpc: CordaRPCOps) {
    data class IssueRequestParams(val amount: Long, val currency: String,
                                  val issueToPartyName: X500Name, val issueToPartyRefAsString: String,
                                  val issuerBankName: X500Name,
                                  val notaryName: X500Name,
                                  val anonymous: Boolean)

    private companion object {
        val logger = loggerFor<BankOfCordaWebApi>()
    }

    @GET
    @Path("date")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCurrentDate(): Any {
        return mapOf("date" to LocalDateTime.now().toLocalDate())
    }

    /**
     *  Request asset issuance
     */
    @POST
    @Path("issue-asset-request")
    @Consumes(MediaType.APPLICATION_JSON)
    fun issueAssetRequest(params: IssueRequestParams): Response {
        // Resolve parties via RPC
        val issueToParty = rpc.partyFromX500Name(params.issueToPartyName)
                ?: throw Exception("Unable to locate ${params.issueToPartyName} in Network Map Service")
        val issuerBankParty = rpc.partyFromX500Name(params.issuerBankName)
                ?: throw Exception("Unable to locate ${params.issuerBankName} in Network Map Service")
        val notaryParty = rpc.partyFromX500Name(params.notaryName)
                ?: throw Exception("Unable to locate ${params.notaryName} in Network Map Service")

        val amount = Amount(params.amount, currency(params.currency))
        val issuerToPartyRef = OpaqueBytes.of(params.issueToPartyRefAsString.toByte())
        val anonymous = params.anonymous

        // invoke client side of Issuer Flow: IssuanceRequester
        // The line below blocks and waits for the future to resolve.
        val status = try {
            rpc.startFlow(::IssuanceRequester, amount, issueToParty, issuerToPartyRef, issuerBankParty, notaryParty, anonymous).returnValue.getOrThrow()
            logger.info("Issue request completed successfully: $params")
            Response.Status.CREATED
        } catch (e: FlowException) {
            Response.Status.BAD_REQUEST
        }
        return Response.status(status).build()
    }
}