// vars/updateGitOps.groovy
// ─────────────────────────────────────────────────────────────────────────────
// Automate GitOps configuration updates
// ─────────────────────────────────────────────────────────────────────────────

def call(Map config = [:]) {

    def gitOpsRepo = config.gitOpsRepo
    def helmValuePath = config.helmValuePath
    def imageTag = config.imageTag
    def appName = config.appName
    def gitOpsBranch = config.get('gitOpsBranch', 'main')

    if (!gitOpsRepo || !helmValuePath || !imageTag || !appName) {
        error("updateGitOps: Missing required arguments. Please ensure gitOpsRepo, helmValuePath, imageTag, and appName are specified.")
    }

    echo "Updating values config tag on GitOps repository: https://${gitOpsRepo}"

    // Clear legacy directories and check out new source structures
    sh "rm -rf gitops-tmp"
    withCredentials([usernamePassword(credentialsId: 'Github-cred', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASSWORD')]) {
        sh "git clone https://\\$GIT_USER:\\$GIT_PASSWORD@${gitOpsRepo} gitops-tmp"
    }

    dir('gitops-tmp') {
        sh "git checkout ${gitOpsBranch} || git checkout -b ${gitOpsBranch}"

        // Ensure values file exists in checkout
        if (!fileExists(helmValuePath)) {
            error("updateGitOps: Target Helm values file at ${helmValuePath} does not exist in GitOps repository.")
        }

        // Modify tag key via yq tool
        sh "yq e '.image.tag = \"${imageTag}\"' -i ${helmValuePath}"

        // Verify change structure
        echo "Updated values configuration block:"
        sh "yq e '.image' ${helmValuePath}"

        // Configure repository parameters
        sh "git config user.email 'ci-pipeline@bushidobrand.com'"
        sh "git config user.name 'Bushido Brand CI/CD Pipeline'"

        // Check for modifications
        def hasChanges = sh(returnStatus: true, script: "git diff --quiet ${helmValuePath}") != 0
        if (hasChanges) {
            sh "git add ${helmValuePath}"
            sh "git commit -m 'chore(${appName}): bump image tag to ${imageTag} [ci skip]'"

            withCredentials([usernamePassword(credentialsId: 'Github-cred', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASSWORD')]) {
                sh "git push origin ${gitOpsBranch}"
            }
            echo "Successfully updated GitOps configurations."
        } else {
            echo "Warning: No modifications detected in Helm values. Skipping commit phase."
        }
    }
    // Clean directory
    sh "rm -rf gitops-tmp"
}
