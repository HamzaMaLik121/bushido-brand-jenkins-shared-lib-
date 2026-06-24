// vars/buildPipeline.groovy
// ─────────────────────────────────────────────────────────────────────────────
// Main Orchestration Pipeline for microservices
// ─────────────────────────────────────────────────────────────────────────────

def call(Map config = [:]) {
    // Validate required configuration parameters
    def requiredKeys = ['appName', 'dockerHubRepo', 'gitOpsRepo', 'helmValuePath', 'sonarProjectKey', 'argoApp']
    for (key in requiredKeys) {
        if (!config.containsKey(key) || !config[key]) {
            error("buildPipeline: '${key}' is required. Set it in your Jenkinsfile's buildPipeline(...) config map.")
        }
    }

    // Default configuration mappings
    def gitOpsBranch  = config.get('gitOpsBranch', 'main')
    def argoAutoSync  = config.get('argoAutoSync', false)
    def slackChannel  = config.get('slackChannel', '#deployments')
    def dockerfile    = config.get('dockerfile', 'Dockerfile')
    def buildContext  = config.get('buildContext', '.')

    pipeline {
        agent { label 'docker-agent' }
        options {
            timestamps()
            ansiColor('xterm')
            timeout(time: 45, unit: 'MINUTES')
            disableConcurrentBuilds()
            buildDiscarder(logRotator(numToKeepStr: '10'))
        }
        environment {
            APP_NAME          = "${config.appName}"
            DOCKER_HUB_REPO   = "${config.dockerHubRepo}"
            GITOPS_REPO       = "${config.gitOpsRepo}"
            HELM_VALUE_PATH   = "${config.helmValuePath}"
            SONAR_KEY         = "${config.sonarProjectKey}"
            ARGO_APP          = "${config.argoApp}"
            GITOPS_BRANCH     = "${gitOpsBranch}"
            ARGO_AUTO_SYNC    = "${argoAutoSync}"
            SLACK_CHANNEL     = "${slackChannel}"
            DOCKERFILE        = "${dockerfile}"
            BUILD_CONTEXT     = "${buildContext}"
            GIT_COMMIT_SHORT  = ""
            FULL_IMAGE        = ""
        }
        stages {
            stage('Checkout') {
                steps {
                    script {
                        checkout scm
                        // Retrieve the 7-character short commit hash for image versioning
                        env.GIT_COMMIT_SHORT = sh(returnStdout: true, script: "git rev-parse --short HEAD").trim()
                        env.FULL_IMAGE = "${env.DOCKER_HUB_REPO}:${env.GIT_COMMIT_SHORT}"
                        echo "Image Build Version Target: ${env.FULL_IMAGE}"
                    }
                }
            }
            stage('OWASP Dependency Check') {
                steps {
                    script {
                        // Scan third-party code packages for security issues
                        runOwaspCheck(appName: env.APP_NAME)
                    }
                }
                post {
                    always {
                        // Publish report inside Jenkins UI via Dependency Check plugin
                        dependencyCheckPublisher pattern: 'reports/dependency-check-report.xml'
                    }
                }
            }
            stage('SonarQube Analysis') {
                steps {
                    script {
                        // Perform Static Application Security Testing (SAST)
                        runSonarScan(projectKey: env.SONAR_KEY)
                    }
                }
            }
            stage('Quality Gate') {
                steps {
                    timeout(time: 5, unit: 'MINUTES') {
                        // Verify check gates in SonarQube. Abort pipeline if criteria aren't met
                        waitForQualityGate abortPipeline: true
                    }
                }
            }
            stage('Docker Build') {
                steps {
                    script {
                        // Build localized container image with appropriate environment parameters
                        buildDockerImage(
                            fullImage: env.FULL_IMAGE,
                            dockerfile: env.DOCKERFILE,
                            context: env.BUILD_CONTEXT
                        )
                    }
                }
            }
            stage('Trivy Image Scan') {
                steps {
                    script {
                        // Verify container layers do not run components containing critical CVEs
                        runTrivyScan(fullImage: env.FULL_IMAGE)
                    }
                }
                post {
                    always {
                        // Preserve vulnerability audit logs
                        archiveArtifacts artifacts: 'trivy-report.json', allowEmptyArchive: true
                    }
                }
            }
            stage('Push to Docker Hub') {
                when {
                    branch pattern: "^(main|master|release/.*)$", comparator: "REGEXP"
                }
                steps {
                    script {
                        // Push verified container tags to registry endpoint
                        pushToDockerHub(fullImage: env.FULL_IMAGE)
                    }
                }
            }
            stage('Update GitOps Repo') {
                when {
                    branch pattern: "^(main|master|release/.*)$", comparator: "REGEXP"
                }
                steps {
                    script {
                        // Update GitOps configurations with the new image tag version
                        updateGitOps(
                            gitOpsRepo: env.GITOPS_REPO,
                            helmValuePath: env.HELM_VALUE_PATH,
                            imageTag: env.GIT_COMMIT_SHORT,
                            appName: env.APP_NAME,
                            gitOpsBranch: env.GITOPS_BRANCH
                        )
                    }
                }
            }
            stage('ArgoCD Sync') {
                when {
                    branch pattern: "^(main|master|release/.*)$", comparator: "REGEXP"
                }
                steps {
                    script {
                        // Direct ArgoCD agent to align active Kubernetes cluster with updated Git tags
                        syncArgoCD(
                            argoApp: env.ARGO_APP,
                            argoAutoSync: env.ARGO_AUTO_SYNC.toBoolean()
                        )
                    }
                }
            }
        }
        post {
            success {
                notifySlack(status: 'SUCCESS', appName: env.APP_NAME, imageTag: env.GIT_COMMIT_SHORT, channel: env.SLACK_CHANNEL)
            }
            failure {
                notifySlack(status: 'FAILURE', appName: env.APP_NAME, imageTag: env.GIT_COMMIT_SHORT, channel: env.SLACK_CHANNEL)
            }
            always {
                // Keep builder environments clean and prevent local credential leakage
                cleanWs()
            }
        }
    }
}
