fun String?.ifNotEmpty(block: (String) -> Unit) {
    if (this != null && this.isNotEmpty()) {
        block(this)
    }
}

fun String.asIcao(): Icao{
    return Icao(this)
}