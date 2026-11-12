package lvluplife.application

/** Compile-only catalogue contract for T065; transport and UI are completed in T069. */
data class ClassStageImage(val stage: Int, val resourceId: String)
data class ClassCatalogEntry(val id: String, val name: String, val images: List<ClassStageImage>)
