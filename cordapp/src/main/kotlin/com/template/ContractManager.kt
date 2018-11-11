package com.template

import net.corda.core.contracts.Amount
import java.lang.IllegalStateException
import java.util.*

object ContractManager {

    fun applyCompleteMilestone(milestoneReference : String, jobState : JobState) : Pair<JobState,Int> {
        val (mileStoneIndex, milestone) = findMilestone(jobState.milestones, milestoneReference)
        val newGrossCumulativeAmount = jobState.grossCumulativeAmount + milestone.amount.quantity.toDouble()
        val retentionAmount = jobState.retentionPercentage * milestone.amount.quantity.toDouble()
        val newNetCumulativeValue = newGrossCumulativeAmount  - retentionAmount

        val toMutableList = jobState.milestones.toMutableList()
        toMutableList[mileStoneIndex] = milestone.copy(status = MilestoneStatus.COMPLETED)

        return Pair(jobState.copy(grossCumulativeAmount = newGrossCumulativeAmount, netCumulativeValue = newNetCumulativeValue, milestones = toMutableList),mileStoneIndex)
    }

    fun applyPayMilestone(milestoneReference: String, jobState: JobState) : Triple<JobState,Amount<Currency>,Int> {
        val (mileStoneIndex, milestone) = findMilestone(jobState.milestones, milestoneReference)
        val retentionAmount = jobState.retentionPercentage * milestone.amount.quantity.toDouble()
        val amountToPay = milestone.amount.copy((milestone.amount.quantity.toDouble() - retentionAmount).toLong())
        val toMutableList = jobState.milestones.toMutableList()
        toMutableList[mileStoneIndex] = milestone.copy(status = MilestoneStatus.PAID)
        return Triple(jobState.copy(milestones = toMutableList, retentionAmount = retentionAmount), amountToPay,mileStoneIndex)
    }

    private fun findMilestone(milestones : List<Milestone>, milestoneReference :  String) : Pair<Int,Milestone> {
        val milestoneResult =  milestones.filter{ milestone -> milestone.reference == milestoneReference }

        if(milestoneResult.isEmpty()){
            throw IllegalStateException("Cannot find Milestone with reference [".plus(milestoneReference).plus("]"))
        }

        return Pair(milestones.indexOf(milestoneResult[0]),milestoneResult[0])
    }


}