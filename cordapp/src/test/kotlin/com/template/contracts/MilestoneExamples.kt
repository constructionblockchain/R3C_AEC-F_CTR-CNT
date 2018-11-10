package com.template.contracts

import com.template.Milestone
import com.template.MilestoneStatus
import net.corda.finance.DOLLARS
import java.time.LocalDate


object MilestoneExamples {

     val milestoneReferences = listOf("M1","M2","M3")

    fun milestones() : List<Milestone> {
        return listOf( fitWindowsMilestone(), fitDoorsMilestone(), fitDoorBellMilestone())
    }

    fun fitWindowsMilestone(): Milestone{
        return fitWindowsMilestone(MilestoneStatus.NOT_STARTED)
    }

    fun fitDoorsMilestone(): Milestone{
        return fitDoorsMilestone(MilestoneStatus.NOT_STARTED)
    }

    fun fitWindowsMilestone(status : MilestoneStatus): Milestone{
        return Milestone(reference = milestoneReferences[0], amount = 100.DOLLARS,  status = status, description = "Fit windows.", expectedEndDate = LocalDate.now(), remarks = "")
    }

    fun fitDoorsMilestone(status : MilestoneStatus): Milestone{
        return Milestone(reference = milestoneReferences[1], amount = 50.DOLLARS,  status = status, description = "Fit doors.", expectedEndDate = LocalDate.now(), remarks = "")
    }

    fun fitDoorBellMilestone() : Milestone {
        return Milestone(reference = milestoneReferences[2], amount = 50.DOLLARS,  status = MilestoneStatus.NOT_STARTED, description = "Add a doorbell.", expectedEndDate = LocalDate.now(), remarks = "")
    }

}