package com.example.a2uie2e

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A custom A2UI component.
 *
 * The agent sends {"component": "PositionRow", ...} and this renders it. The
 * branching on instrument kind lives here, in app code that ships in the
 * binary and goes through code review — not in a payload and not generated.
 */
object PositionRow : A2uiComponent {

  // ── the schema this component advertises
  // The concrete property classes are internal; build them through the
  // A2uiProperty factories. "dynamic" means the agent may bind it to a path.
  private val Symbol = A2uiProperty.dynamicString("symbol", true, "Ticker or identifier.")
  private val Value = A2uiProperty.dynamicString("value", true, "Market value, preformatted.")
  private val Kind = A2uiProperty.dynamicString("kind", false, "equity | option | bond")
  private val Detail = A2uiProperty.dynamicString("detail", false, "Instrument-specific detail.")

  override val name: String = "PositionRow"
  override val description: String = "One holding, rendered by instrument kind."
  override val properties: List<A2uiProperty<*>> = listOf(Symbol, Value, Kind, Detail)

  // Content is an EXTENSION on A2uiComponentScope, not a plain method, so
  // bind() and dispatchAction() are in scope without being passed in.
  @Composable
  override fun A2uiComponentScope.Content(
    properties: A2uiComponentProperties,
    modifier: Modifier,
  ) {
    // bind() is an extension on the PROPERTIES, not on the scope:
    //   fun <T : Any> A2uiComponentProperties.bind(p: DynamicA2uiProperty<T>): T?
    val symbol = properties.bind(Symbol) ?: ""
    val value = properties.bind(Value) ?: ""
    val kind = properties.bind(Kind) ?: "equity"
    val detail = properties.bind(Detail) ?: ""

    Row(modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(symbol, style = MaterialTheme.typography.titleMedium,
             fontWeight = FontWeight.SemiBold)
        if (detail.isNotEmpty()) {
          Text(detail, style = MaterialTheme.typography.bodySmall,
               color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Text(
        value,
        style = MaterialTheme.typography.titleMedium,
        color = when (kind) {
          "option" -> Color(0xFF8E24AA)
          "bond" -> Color(0xFF00796B)
          else -> MaterialTheme.colorScheme.onSurface
        },
      )
    }
  }
}

/** The catalog id our agent must name in createSurface. */
const val POSITIONS_CATALOG_ID = "example.com:positions-v1"

/**
 * v0.9.1 allows exactly ONE catalogId per surface, so a custom component
 * cannot sit in a second catalog alongside the Material one -- the surface
 * would only resolve against whichever it names. Build one catalog that
 * EXTENDS Material's with our component instead. (v1.0 adds mixable
 * catalogs and removes this constraint.)
 */
fun extendedCatalog(base: A2uiCatalog): A2uiCatalog =
  A2uiCatalog(
    POSITIONS_CATALOG_ID,
    base.components + PositionRow,
    base.functions,
    base.themeSchema,
  )
