package lvluplife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    val sessionController get() = (application as LvlUpApplication).graph.session
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        if(android.os.Build.VERSION.SDK_INT>=35) enableEdgeToEdge()
        val graph = (application as LvlUpApplication).graph
        val google = lvluplife.data.AndroidGoogleSignIn.create(this)
        setContent {
            lvluplife.ui.theme.LvlUpTheme { lvluplife.ui.AppNavigation(graph.session, google, graph.accounts, graph.records, graph.progress, graph.activities, graph.history, graph.corrections, graph.resets) }
        }
    }
}
