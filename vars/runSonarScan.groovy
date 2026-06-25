// vars/runSonarScan.groovy
// ─────────────────────────────────────────────────────────────────────────────
// SonarQube SAST Scanner Execution
// ─────────────────────────────────────────────────────────────────────────────

def call(Map config = [:]) {
    def projectKey = config.projectKey
    if (!projectKey) {
        error("runSonarScan: 'projectKey' is a mandatory argument to configure Sonar scan routing.")
    }

    echo "Running SonarQube static code quality metrics analysis on: ${projectKey}"

    // Wrap execution within Sonar environment definition to automatically load configs
    withSonarQubeEnv('SonarQube') {
        withCredentials([
            // SONAR is a single Secret Text credential containing the SonarQube server URL
            // The SonarQube token is configured globally in Jenkins under Manage Jenkins → Configure System → SonarQube servers
            string(credentialsId: 'SONAR', variable: 'SONAR_HOST_URL')
        ]) {
            sh """
                sonar-scanner \
                    -Dsonar.projectKey="${projectKey}" \
                    -Dsonar.projectName="${projectKey}" \
                    -Dsonar.sources=. \
                    -Dsonar.host.url="\$SONAR_HOST_URL" \
                    -Dsonar.qualitygate.wait=false \
                    -Dsonar.exclusions="**/vendor/**,**/node_modules/**,**/*.test.*,**/*.spec.*,**/test/**,**/tests/**,**/__mocks__/**"
            """
        }
    }
    echo "Code scanner telemetry successfully sent to SonarQube console."
}
