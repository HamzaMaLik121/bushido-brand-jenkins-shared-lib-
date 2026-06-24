// vars/pushToDockerHub.groovy
// ─────────────────────────────────────────────────────────────────────────────
// Securely push built tags to Docker Hub
// ─────────────────────────────────────────────────────────────────────────────

def call(Map config = [:]) {
    def fullImage = config.fullImage
    if (!fullImage) {
        error("pushToDockerHub: 'fullImage' string must be provided.")
    }

    // Deduce image namespace by splitting the tag from target
    def imageBase = fullImage.split(':')[0]

    echo "Pushing artifacts: ${fullImage} and ${imageBase}:latest to Docker Hub registry"

    // Utilize credentials binding and enforce logout in validation block
    withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_TOKEN')]) {
        sh "echo \$DOCKER_TOKEN | docker login -u \$DOCKER_USER --password-stdin"
        try {
            // Push commit-specific tag
            sh "docker push ${fullImage}"

            // Re-tag target as 'latest' for tracking general release builds
            sh "docker tag ${fullImage} ${imageBase}:latest"
            sh "docker push ${imageBase}:latest"
        } finally {
            // Force authorization cleanup
            sh "docker logout"
        }
    }
    echo "Registry push complete."
}
