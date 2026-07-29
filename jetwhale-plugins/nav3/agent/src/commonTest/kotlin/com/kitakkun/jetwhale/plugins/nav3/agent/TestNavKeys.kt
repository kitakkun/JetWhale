package com.kitakkun.jetwhale.plugins.nav3.agent

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Stand-ins for the keys a debugged app would define: an object, a class with a defaulted and a
// nullable field, an enum field, and a closed hierarchy.

@SerialName("Home")
@Serializable
data object HomeKey : NavKey

@SerialName("Detail")
@Serializable
data class DetailKey(
    val id: String,
    val page: Int = 0,
    val note: String?,
) : NavKey

@SerialName("Catalog")
@Serializable
data class CatalogKey(
    val layout: CatalogLayout,
    val tags: List<String>,
) : NavKey

@Serializable
enum class CatalogLayout { Grid, List }

@Serializable
sealed interface Screen : NavKey {
    @SerialName("screen.home")
    @Serializable
    data object Home : Screen

    @SerialName("screen.detail")
    @Serializable
    data class Detail(val id: String) : Screen
}
