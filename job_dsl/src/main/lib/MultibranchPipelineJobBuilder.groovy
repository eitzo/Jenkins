import java.security.MessageDigest

import javaposse.jobdsl.dsl.DslFactory
import javaposse.jobdsl.dsl.Job
import javaposse.jobdsl.dsl.jobs.MultibranchWorkflowJob

class MultibranchPipelineJobBuilder extends JobBuilder {

    MultibranchPipelineJobBuilder(MultibranchWorkflowJob job, DslFactory dslFactory, def projectData) {
        super(job, dslFactory, projectData)
    }

    void gitHubOrganization() {
        job.branchSources {
            branchSource {
                source {
                    github {
                        id(md5sum(projectData.githubUrl))
                        repoOwner(projectData.repoOwner)
                        repository(projectData.repoName)
                        apiUri('https://api.github.com')
                        credentialsId(projectData.githubOrganizationsJenkinsCredentialsRefId)

                        traits {
                            cleanBeforeCheckoutTrait()
                        }
                    }
                }
            } 
        }

        job.orphanedItemStrategy {
            discardOldItems {
                numToKeep(30)
            }
        }

        job.triggers {
            // Check every hour, should be triggered anyhow
            periodic(60)
        }

        job.configure {
            // workaround for JENKINS-46202 (https://issues.jenkins-ci.org/browse/JENKINS-46202)
            def traits = it / sources / data / 'jenkins.branch.BranchSource' / source / traits
            // Discover Branches: All Branches
            traits << 'org.jenkinsci.plugins.github__branch__source.BranchDiscoveryTrait' {
                strategyId(3)
            }
            // Discover pull requests from forks
            //    - No merge, build the PR (With merge we would trigger a complete rebuild of all PRs if develop is updated,
            //            which can't be handled by current slaves ATM)
            //    - Trust: From Users with Admin or Write permission
            traits << 'org.jenkinsci.plugins.github__branch__source.ForkPullRequestDiscoveryTrait' {
                strategyId(2)
                trust(class: 'org.jenkinsci.plugins.github_branch_source.ForkPullRequestDiscoveryTrait$TrustPermission')
            }
            // Discover pull requests from origin: No merge, build the PR (With merge we would trigger a complete
            //            rebuild of all PRs if develop is updated, which can't be handled by current slaves ATM)
            traits << 'org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait' {
                strategyId(2)
            }
        }
    }

    void jenkinsfilePath(String jenkinsfilePath) {
        job.configure {
            it / factory(class: 'org.jenkinsci.plugins.workflow.multibranch.WorkflowBranchProjectFactory') {
                scriptPath(jenkinsfilePath)
            }
        }
    }

    void labelForDockerBuild(String label) {
        job.configure {
            it / 'properties' / 'org.jenkinsci.plugins.pipeline.modeldefinition.config.FolderConfig' {
                dockerLabel(label)
            }
        }
    }

    def md5sum(String input) {
        MessageDigest digest = MessageDigest.getInstance("MD5")
        digest.update(input.bytes)
        return new BigInteger(1, digest.digest()).toString(16).padLeft(32, '0')
    }
}