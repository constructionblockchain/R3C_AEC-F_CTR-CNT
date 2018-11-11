package com.template

import net.corda.core.contracts.Amount
import java.lang.IllegalStateException
import java.util.*

class ContractManager {

    fun applyCompleteMilestone(milestoneReference : String, jobState : JobState) : JobState {
        var (mileStoneIndex, milestone) = findMilestone(jobState.milestones, milestoneReference)
        val newGrossCumulativeAmount = jobState.grossCumulativeAmount + milestone.amount.quantity.toDouble()
        val retentionAmount = jobState.retentionPercentage * milestone.amount.quantity.toDouble()
        val newNetCumulativeValue = jobState.netCumulativeValue  + retentionAmount

        var toMutableList = jobState.milestones.toMutableList()
        toMutableList[mileStoneIndex] = milestone.copy(status = MilestoneStatus.COMPLETED)

        return jobState.copy(grossCumulativeAmount = newGrossCumulativeAmount, netCumulativeValue = newNetCumulativeValue)
    }

    fun applyPaidMilestyone(milestoneReference: String , jobState: JobState) : Pair<JobState,Amount<Currency> > {
        var (mileStoneIndex, milestone) = findMilestone(jobState.milestones, milestoneReference)

        val doubleToPay = jobState.retentionPercentage * milestone.amount.quantity.toDouble()
        val amountToPay = milestone.amount.copy(doubleToPay.toLong())

        var toMutableList = jobState.milestones.toMutableList()
        toMutableList[mileStoneIndex] = milestone.copy(status = MilestoneStatus.PAID)

        return Pair(jobState.copy(milestones = toMutableList), amountToPay)
    }

    private fun findMilestone(milestones : List<Milestone>, milestoneReference :  String) : Pair<Int,Milestone> {
        val milestoneResult =  milestones.filter{ milestone -> milestone.reference == milestoneReference }

        if(milestoneResult.isEmpty()){
            throw IllegalStateException("Cannot find Milestone with reference [".plus(milestoneReference).plus("]"))
        }

        return Pair(milestones.indexOf(milestoneResult[0]),milestoneResult[0])
    }


}