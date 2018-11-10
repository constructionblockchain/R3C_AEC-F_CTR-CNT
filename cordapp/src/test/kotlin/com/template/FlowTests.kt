package com.template

import com.template.contracts.MilestoneExamples
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.queryBy
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FlowTests {
    private val network = MockNetwork(listOf("com.template", "net.corda.finance.contracts.asset"))
    private val a = network.createNode()
    private val b = network.createNode()



    private val milestones = MilestoneExamples.milestones()

    private val milestoneNames = milestones.map { milestone -> milestone.description }
    private val milestoneAmounts = milestones.map { milestone -> milestone.amount }

    private val firstMilestoneReference = MilestoneExamples.milestoneReferences[0]
    private val firstMilestoneIndex = 0


    init {
        b.registerInitiatedFlow(AgreeJobFlowResponder::class.java)
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    fun agreeJob(): UniqueIdentifier {
        val flow = AgreeJobFlow(contractor =  b.info.chooseIdentity(),
                contractAmount = 200.0,
                retentionPercentage = 0.2,
                allowPaymentOnAccount = true,
                milestones = milestones,
                notaryToUse = network.defaultNotaryIdentity)

        val resultFuture = a.startFlow(flow)
        network.runNetwork()
        return resultFuture.get()
    }

    fun startJob(linearId: UniqueIdentifier, milestoneReference: String): UniqueIdentifier {
        val flow = StartMilestoneFlow(linearId, milestoneReference)

        val resultFuture = b.startFlow(flow)
        network.runNetwork()
        return resultFuture.get()
    }

    fun finishJob(linearId: UniqueIdentifier, milestoneReference: String): UniqueIdentifier {
        val flow = CompleteMilestoneFlow(linearId, milestoneReference)

        val resultFuture = b.startFlow(flow)
        network.runNetwork()
        return resultFuture.get()
    }

    fun inspectJob(linearId: UniqueIdentifier, isApproved: Boolean, milestoneReference: String): UniqueIdentifier {
        val flow = AcceptOrRejectFlow(linearId, isApproved, milestoneReference)

        val resultFuture = a.startFlow(flow)
        network.runNetwork()
        return resultFuture.get()
    }

    fun issueCash() {
        val flow = IssueCashFlow(650.DOLLARS, notaryToUse = network.defaultNotaryIdentity)
        val resultFuture = a.startFlow(flow)
        network.runNetwork()
        resultFuture.get()
    }

    fun payJob(linearId: UniqueIdentifier, milestoneReference: String): UniqueIdentifier {
        val flow = PayFlow(linearId, milestoneReference)
        val resultFuture = a.startFlow(flow)
        network.runNetwork()
        return resultFuture.get()
    }

    @Test
    fun `golden path agree job flow`() {
        agreeJob()

        listOf(a, b).forEach { node ->
            node.transaction {
                val jobStatesAndRefs = node.services.vaultService.queryBy<JobState>().states
                assertEquals(1, jobStatesAndRefs.size)

                val jobState = jobStatesAndRefs.single().state.data
                assertEquals(a.info.chooseIdentity(), jobState.developer)
                assertEquals(b.info.chooseIdentity(), jobState.contractor)
                assertEquals(milestones, jobState.milestones)
            }
        }
    }

    @Test
    fun `golden path start job flow`() {
        val linearId = agreeJob()
        startJob(linearId, firstMilestoneReference)

        listOf(a, b).forEach { node ->
            node.transaction {
                val jobStatesAndRefs = node.services.vaultService.queryBy<JobState>().states
                assertEquals(1, jobStatesAndRefs.size)

                val jobState = jobStatesAndRefs.single().state.data
                assertEquals(a.info.chooseIdentity(), jobState.developer)
                assertEquals(b.info.chooseIdentity(), jobState.contractor)


                val milestonesState = jobState.milestones
                val milestoneStarted = milestonesState[firstMilestoneIndex]
                assertEquals(milestoneNames, milestonesState.map { it.description })
                assertEquals(milestoneAmounts, milestonesState.map { it.amount })
                assertEquals(MilestoneStatus.STARTED, milestoneStarted.status)

                assertEquals(milestones.subList(1, milestones.size), milestonesState.subList(1, milestones.size))

            }
        }
    }

    @Test
    fun `golden path finish job flow`() {
        val linearId = agreeJob()
        startJob(linearId, firstMilestoneReference)
        finishJob(linearId, firstMilestoneReference)

        listOf(a, b).forEach { node ->
            node.transaction {
                val jobStatesAndRefs = node.services.vaultService.queryBy<JobState>().states
                assertEquals(1, jobStatesAndRefs.size)

                val jobState = jobStatesAndRefs.single().state.data
                assertEquals(a.info.chooseIdentity(), jobState.developer)
                assertEquals(b.info.chooseIdentity(), jobState.contractor)


                val milestonesState = jobState.milestones
                val milestoneStarted = milestonesState[firstMilestoneIndex]
                assertEquals(milestoneNames, milestonesState.map { it.description })
                assertEquals(milestoneAmounts, milestonesState.map { it.amount })
                assertEquals(MilestoneStatus.COMPLETED, milestoneStarted.status)

                assertEquals(milestones.subList(1, milestones.size), milestonesState.subList(1, milestones.size))

            }
        }
    }

    @Test
    fun `golden path reject job flow`() {
        val linearId = agreeJob()
        startJob(linearId, firstMilestoneReference)
        finishJob(linearId, firstMilestoneReference)
        inspectJob(linearId, false, firstMilestoneReference)

        listOf(a, b).forEach { node ->
            node.transaction {
                val jobStatesAndRefs = node.services.vaultService.queryBy<JobState>().states
                assertEquals(1, jobStatesAndRefs.size)

                val jobState = jobStatesAndRefs.single().state.data

                assertEquals(a.info.chooseIdentity(), jobState.developer)
                assertEquals(b.info.chooseIdentity(), jobState.contractor)
                assertEquals(MilestoneStatus.STARTED, jobState.milestones[0].status)
                assertEquals(milestones[0].description, jobState.milestones[0].description)
                assertEquals(milestones[0].amount, jobState.milestones[0].amount)
                assertEquals(milestones.subList(1, milestones.size), jobState.milestones.subList(1, milestones.size))
            }
        }
    }

    @Test
    fun `golden path accept first job flow`() {
        val linearId = agreeJob()
        startJob(linearId, firstMilestoneReference)
        finishJob(linearId, firstMilestoneReference)
        inspectJob(linearId, true, firstMilestoneReference)

        listOf(a, b).forEach { node ->
            node.transaction {
                val jobStatesAndRefs = node.services.vaultService.queryBy<JobState>().states
                assertEquals(1, jobStatesAndRefs.size)

                val jobState = jobStatesAndRefs.single().state.data
                assertEquals(a.info.chooseIdentity(), jobState.developer)
                assertEquals(b.info.chooseIdentity(), jobState.contractor)

                assertEquals(MilestoneStatus.ACCEPTED, jobState.milestones[0].status)
                assertEquals(milestones[0].description, jobState.milestones[0].description)
                assertEquals(milestones[0].amount, jobState.milestones[0].amount)

                assertEquals(milestones.subList(1, milestones.size), jobState.milestones.subList(1, milestones.size))
            }
        }
    }

    @Test
    fun `golden path pay first job flow`() {
        val linearId = agreeJob()
        startJob(linearId, firstMilestoneReference)
        finishJob(linearId, firstMilestoneReference)
        inspectJob(linearId, true, firstMilestoneReference)
        issueCash()
        payJob(linearId, firstMilestoneReference)

        listOf(a, b).forEach { node ->
            node.transaction {
                val jobStatesAndRefs = node.services.vaultService.queryBy<JobState>().states
                assertEquals(1, jobStatesAndRefs.size)

                val jobState = jobStatesAndRefs.single().state.data
                assertEquals(a.info.chooseIdentity(), jobState.developer)
                assertEquals(b.info.chooseIdentity(), jobState.contractor)

                assertEquals(MilestoneStatus.PAID, jobState.milestones[0].status)
                assertEquals(milestones[0].description, jobState.milestones[0].description)
                assertEquals(milestones[0].amount, jobState.milestones[0].amount)

                assertEquals(milestones.subList(1, milestones.size), jobState.milestones.subList(1, milestones.size))
            }
        }

        a.transaction {
            val cashStatesAndRefs = a.services.vaultService.queryBy<Cash.State>().states
            val balance = cashStatesAndRefs.sumBy { it.state.data.amount.quantity.toInt() }
            assertEquals(55000, balance)
        }

        b.transaction {
            val cashStatesAndRefs = a.services.vaultService.queryBy<Cash.State>().states
            val balance = cashStatesAndRefs.sumBy { it.state.data.amount.quantity.toInt() }
            assertEquals(10000, balance)
        }
    }

    @Test
    fun `golden path pay all jobs flow`() {
        val linearId = agreeJob()
        MilestoneExamples.milestoneReferences.forEach { reference -> startJob(linearId, reference) }
        MilestoneExamples.milestoneReferences.forEach { reference -> finishJob(linearId, reference) }
        MilestoneExamples.milestoneReferences.forEach { reference -> inspectJob(linearId, true, reference) }
        issueCash()
        MilestoneExamples.milestoneReferences.forEach { reference -> payJob(linearId, reference) }

        listOf(a, b).forEach { node ->
            node.transaction {
                val jobStatesAndRefs = node.services.vaultService.queryBy<JobState>().states
                assertEquals(1, jobStatesAndRefs.size)

                val jobState = jobStatesAndRefs.single().state.data
                assertEquals(a.info.chooseIdentity(), jobState.developer)
                assertEquals(b.info.chooseIdentity(), jobState.contractor)

                jobState.milestones.forEachIndexed { index, milestone ->
                    assertEquals(MilestoneStatus.PAID, milestone.status)
                    assertEquals(milestones[index].description, milestone.description)
                    assertEquals(milestones[index].amount, milestone.amount)
                }
            }
        }

        a.transaction {
            val cashStatesAndRefs = a.services.vaultService.queryBy<Cash.State>().states
            val balance = cashStatesAndRefs.sumBy { it.state.data.amount.quantity.toInt() }
            assertEquals(0, balance)
        }

        b.transaction {
            val cashStatesAndRefs = a.services.vaultService.queryBy<Cash.State>().states
            val balance = cashStatesAndRefs.sumBy { it.state.data.amount.quantity.toInt() }
            assertEquals(65000, balance)
        }
    }
}
