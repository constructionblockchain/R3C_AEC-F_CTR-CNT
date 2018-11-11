package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.lang.IllegalStateException

/**
 * Change the status of a [Milestone] in a [JobState] from [MilestoneStatus.STARTED] to [MilestoneStatus.COMPLETED].
 *
 * Should be run by the contractor.
 *
 * @param linearId the [JobState] to update.
 * @param milestoneIndex the index of the [Milestone] to be updated in the [JobState].
 */
@InitiatingFlow
@StartableByRPC
class CompleteMilestoneFlow(val linearId: UniqueIdentifier, private val milestoneReference: String) : FlowLogic<UniqueIdentifier>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): UniqueIdentifier {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                linearId = listOf(linearId))
        val results = serviceHub.vaultService.queryBy<JobState>(queryCriteria)
        val inputStateAndRef = results.states.singleOrNull()
                ?: throw FlowException("There is no JobState with linear ID $linearId")
        val inputState = inputStateAndRef.state.data

        if (inputState.contractor != ourIdentity) throw IllegalStateException("The contractor must start this flow.")

        var (newJobState, milestoneIndex) = ContractManager.applyCompleteMilestone(milestoneReference, inputState)

        val finishJobCommand = Command(
                JobContract.Commands.FinishMilestone(milestoneIndex),
                listOf(ourIdentity.owningKey))

        val transactionBuilder = TransactionBuilder(inputStateAndRef.state.notary)
                .addInputState(inputStateAndRef)
                .addOutputState(newJobState, JobContract.ID)
                .addCommand(finishJobCommand)

        transactionBuilder.verify(serviceHub)

        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        subFlow(FinalityFlow(signedTransaction))

        return newJobState.linearId
    }

    private fun findMilestone(milestones : List<Milestone>, milestoneReference :  String) : Int {
        val milestoneResult =  milestones.filter{ milestone -> milestone.reference == milestoneReference }

        if(milestoneResult.isEmpty()){
            throw IllegalStateException("Cannot find Milestone with reference [".plus(milestoneReference).plus("]"))
        }

        return milestones.indexOf(milestoneResult[0])
    }
}