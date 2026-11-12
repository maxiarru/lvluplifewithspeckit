package lvluplife.application

/** Server-authored visual metadata; Android only compares minimumLevel with confirmed general level. */
data class ClassStageImage(val stage: Int, val resourceId: String, val minimumLevel: Int)
data class ClassCatalogEntry(val id: String, val name: String, val images: List<ClassStageImage>)
