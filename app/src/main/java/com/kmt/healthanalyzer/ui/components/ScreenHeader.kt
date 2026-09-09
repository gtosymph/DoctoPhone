package com.kmt.healthanalyzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * En-tête d'écran de la coquille : un filet coloré, un grand titre, une action optionnelle.
 *
 * [accent] est une identité de navigation propre à l'écran, pas un domaine de santé — la
 * charte « une couleur par domaine » ([com.kmt.healthanalyzer.ui.theme.DomainColors]) reste
 * réservée aux données de santé elles-mêmes (l'objectif de sommeil des réglages, par
 * exemple). Les deux registres de couleur ne se mélangent pas, pour ne pas laisser croire
 * qu'un écran entier porte sur un seul domaine.
 */
@Composable
fun ScreenHeader(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (subtitle != null) 46.dp else 32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * Étiquette de section « éditorial coloré » : une petite puce colorée devant un titre en
 * petites capitales, pour hiérarchiser un écran qui regroupe plusieurs blocs (les réglages,
 * par exemple) sans dessiner une nouvelle carte pour chaque titre.
 */
@Composable
fun SectionLabel(text: String, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accent),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
