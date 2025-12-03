import jenkins.model.Jenkins
import javaposse.jobdsl.plugin.JenkinsJobManagement
import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.dsl.GeneratedItems

def jenkins = Jenkins.instance
if (!jenkins.isQuietingDown()) {
    println "🚀 Exécution du Job DSL pour créer les jobs Whanos..."
    
    try {
        def jobDslScript = new File('/var/jenkins_home/casc_configs/job_dsl.groovy')
        
        if (jobDslScript.exists()) {
            def workspace = new File('.')
            def jobManagement = new JenkinsJobManagement(System.out, [:], workspace)
            
            GeneratedItems items = new DslScriptLoader(jobManagement).runScript(jobDslScript.text)
            
            println "✅ Job DSL exécuté avec succès!"
            println "📦 Jobs créés:"
            items.jobs.each { job ->
                println "   - ${job.jobName}"
            }
            println "📁 Vues créées:"
            items.views.each { view ->
                println "   - ${view.viewName}"
            }
        } else {
            println "⚠️  Fichier job_dsl.groovy introuvable: ${jobDslScript.absolutePath}"
        }
    } catch (Exception e) {
        println "❌ Erreur lors de l'exécution du Job DSL:"
        e.printStackTrace()
    }
} else {
    println "⏸️  Jenkins en cours d'arrêt, Job DSL non exécuté"
}
