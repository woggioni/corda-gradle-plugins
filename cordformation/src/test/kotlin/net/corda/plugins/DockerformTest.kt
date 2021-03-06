package net.corda.plugins

import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DockerformTest : BaseformTest() {

    private fun getDockerCompose(): Path = testProjectDir.resolve("build").resolve("nodes").resolve("docker-compose.yml")

    @Test
    fun `fail to deploy a node with cordapp dependency with missing dockerImage tag`() {
        val runner = getStandardGradleRunnerFor(
                "DeploySingleNodeWithCordappWithDocker.gradle",
                "prepareDockerNodes")

        val result = runner.buildAndFail()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("Caused by: org.gradle.api.InvalidUserDataException: No value has been specified for property 'dockerImage'.")
    }

    @Test
    fun `deploy a node with cordapp dependency and network configuration` () {
        val runner = getStandardGradleRunnerFor(
                "DeploySingleNodeWithCordappWithNetworkConfigWithDocker.gradle",
                "prepareDockerNodes")

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }

    @Test
    fun `deploy a three node cordapp` () {
        val runner = getStandardGradleRunnerFor(
                "DeployThreeNodeCordappWithExternalDBSettingsWithDocker.gradle",
                "prepareDockerNodes")

        val bankOfCordaNodeName = "BankOfCorda"
        val bigCorporationNodeName = "BigCorporation"

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
        assertThat(getNodeCordappJar(bankOfCordaNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(bankOfCordaNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bankOfCordaNodeName)).isRegularFile()
        assertThat(getNodeCordappJar(bigCorporationNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(bigCorporationNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bigCorporationNodeName)).isRegularFile()
    }

    @Test
    fun `deploy a three node cordapp with network configuration` () {
        val runner = getStandardGradleRunnerFor(
                "DeployThreeNodeCordappWithExternalDBSettingsWithNetworkConfigWithDocker.gradle",
                "prepareDockerNodes")

        val bankOfCordaNodeName = "BankOfCorda"
        val bigCorporationNodeName = "BigCorporation"

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
        assertThat(getNodeCordappJar(bankOfCordaNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(bankOfCordaNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bankOfCordaNodeName)).isRegularFile()
        assertThat(getNodeCordappJar(bigCorporationNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(bigCorporationNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bigCorporationNodeName)).isRegularFile()
    }

    @Test
    fun `deploy a node with cordapp dependency with db settings`() {
        val runner = getStandardGradleRunnerFor(
                "DeploySingleNodeWithExternalDBSettingsWithDocker.gradle",
                "prepareDockerNodes")

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }

    @Suppress("unchecked_cast")
    @Test
    fun `deploy two nodes with cordapp dependencies`() {
        val runner = getStandardGradleRunnerFor(
                "DeployTwoNodeCordappWithDocker.gradle",
                "prepareDockerNodes")

        val result = runner.build()

        val bankOfCordaNodeName = "BankOfCorda"

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
        assertThat(getNodeCordappJar(bankOfCordaNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(bankOfCordaNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bankOfCordaNodeName)).isRegularFile()

        val notaryConfigPath = getNodeConfig(notaryNodeName)
        assertThat(notaryConfigPath).isRegularFile()

        val notaryConfig = ConfigFactory.parseFile(notaryConfigPath.toFile())
        assertTrue(notaryConfig.hasPath("rpcSettings.address"))
        assertTrue(notaryConfig.hasPath("rpcSettings.adminAddress"))
        assertEquals(10003, ConfigurationUtils.parsePort(notaryConfig.getString("rpcSettings.address")))
        assertEquals(10043, ConfigurationUtils.parsePort(notaryConfig.getString("rpcSettings.adminAddress")))

        val bankOfCordaConfigPath = getNodeConfig(bankOfCordaNodeName)
        assertThat(bankOfCordaConfigPath).isRegularFile()

        val bankOfCordaConfig = ConfigFactory.parseFile(bankOfCordaConfigPath.toFile())
        assertTrue(bankOfCordaConfig.hasPath("rpcSettings.address"))
        assertTrue(bankOfCordaConfig.hasPath("rpcSettings.adminAddress"))
        assertEquals(10006, ConfigurationUtils.parsePort(bankOfCordaConfig.getString("rpcSettings.address")))
        assertEquals(10046, ConfigurationUtils.parsePort(bankOfCordaConfig.getString("rpcSettings.adminAddress")))

        val dockerComposePath = getDockerCompose()

        val yaml = dockerComposePath.toFile().bufferedReader().use { reader ->
            Yaml().load(reader) as Map<String, Any>
        }
        assertThat(yaml).containsKey("services")

        val services = yaml["services"] as Map<String, Any>
        assertThat(services).containsKey("notary-service")
        assertThat(services).containsKey("bankofcorda")

        val notaryService = services["notary-service"] as Map<String, Any>
        assertThat(notaryService).containsKey("ports")
        val notaryServicePortsList = notaryService["ports"] as List<Any>
        assertThat(notaryServicePortsList).contains("22234:22234")

        val bankOfCorda = services["bankofcorda"] as Map<String, Any>
        assertThat(bankOfCorda).containsKey("ports")
        val bankOfCordaPortsList = bankOfCorda["ports"] as List<Any>
        assertThat(bankOfCordaPortsList).contains("22235:22235")
    }
}
