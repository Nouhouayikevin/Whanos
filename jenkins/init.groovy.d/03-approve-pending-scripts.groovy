import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval


println "🔓 Approving pending Job DSL script signatures..."

def scriptApproval = ScriptApproval.get()

// Approuver toutes les signatures en attente
def pendingSignatures = scriptApproval.getPendingSignatures()
if (pendingSignatures) {
    pendingSignatures.each { signature ->
        scriptApproval.approveSignature(signature.signature)
        println "✅ Auto-approved: ${signature.signature}"
    }
    println "✅ Approved ${pendingSignatures.size()} pending signature(s)"
} else {
    println "ℹ️  No pending signatures to approve"
}


def pendingScripts = scriptApproval.getPendingScripts()
if (pendingScripts) {
    pendingScripts.each { script ->
        scriptApproval.approveScript(script.hash)
        println "✅ Auto-approved script: ${script.hash}"
    }
    println "✅ Approved ${pendingScripts.size()} pending script(s)"
} else {
    println "ℹ️  No pending scripts to approve"
}

println "✅ Script approval completed"
