package lvluplife.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import lvluplife.application.SessionRoute

/** Bottom-bar tab of [route] (merged tree), told apart from screen text with the same label. */
fun isTab(route: SessionRoute): SemanticsMatcher =
    hasText(tabLabel(route)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
