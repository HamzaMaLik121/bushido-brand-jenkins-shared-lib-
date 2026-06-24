// vars/notifySlack.groovy
// ─────────────────────────────────────────────────────────────────────────────
// Format and send Slack notifications
// ─────────────────────────────────────────────────────────────────────────────

def call(Map config = [:]) {
    def status = config.status
    def appName = config.appName
    def imageTag = config.imageTag
    def channel = config.get('channel', '#deployments')

    def color = (status == 'SUCCESS') ? 'good' : 'danger'
    def emoji = (status == 'SUCCESS') ? ':white_check_mark:' : ':x:'

    def message = """${emoji} [${appName}] Pipeline Status: ${status}
• Branch: `${env.BRANCH_NAME ?: 'unknown'}`
• Image Tag: `${imageTag}`
• Build: <${env.BUILD_URL}|#${env.BUILD_NUMBER}>
• Duration: ${currentBuild.durationString}"""

    slackSend(channel: channel, color: color, message: message)
}
