inline fun String?.ifNotEmpty(crossinline block: (String) -> Unit): Unit {
    if (this != null && this.isNotEmpty()) {
        block(this)
    }
}